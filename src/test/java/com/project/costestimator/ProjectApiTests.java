package com.project.costestimator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void exposesInteractiveOpenApiDocumentation() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Construction Cost Estimator API"))
                .andExpect(jsonPath("$.paths['/api/v1/projects']").exists());
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void createsCompleteProjectStructureAndCalculatesCost() throws Exception {
        String equipmentId = id(postJson("/api/v1/resources/equipment", """
                {"code":"EQ-01","name":"Excavator","equipmentType":"EXCAVATOR","owned":false}
                """));
        String operatorId = id(postJson("/api/v1/resources/personnel", """
                {"code":"PER-01","name":"Operator","profession":"Equipment operator","skillLevel":"EXPERIENCED"}
                """));
        String materialId = id(postJson("/api/v1/resources/materials", """
                {"code":"MAT-01","name":"Geotextile","materialType":"GEOSYNTHETIC","defaultUnit":"SQUARE_METER"}
                """));
        postJson("/api/v1/resources/" + equipmentId + "/cost-components", """
                {"category":"RENTAL","name":"Daily rental","calculationBasis":"PER_DAY","unitPrice":1000,"unit":"DAY"}
                """);
        postJson("/api/v1/resources/" + equipmentId + "/cost-components", """
                {"category":"FUEL","name":"Diesel price","calculationBasis":"PER_UNIT","unitPrice":10,"unit":"LITER"}
                """);
        postJson("/api/v1/resources/" + equipmentId + "/fuel-consumptions", """
                {"fuelType":"DIESEL","consumptionPerHour":2,"consumptionUnit":"LITER"}
                """);
        postJson("/api/v1/resources/" + operatorId + "/cost-components", """
                {"category":"SALARY","name":"Hourly wage","calculationBasis":"PER_HOUR","unitPrice":25,"unit":"HOUR"}
                """);
        String projectId = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"MAR-01","name":"Marine excavation","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-10","currencyCode":"TRY"}
                """)).path("project").path("id").asText();
        String estimateId = id(postJson("/api/v1/projects/" + projectId + "/estimates", "{\"name\":\"Estimate v1\"}"));
        String wbsId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items", "{\"code\":\"1\",\"name\":\"Dredging\"}"));
        String activityId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items/" + wbsId + "/activities", """
                {"code":"A-1","name":"Seabed excavation","plannedDuration":2,"durationUnit":"DAY","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-02"}
                """));
        mvc.perform(put("/api/v1/projects/{p}/estimates/{e}/activities/{a}", projectId, estimateId, activityId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"A-1","name":"Seabed excavation","plannedDuration":3,"durationUnit":"DAY","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-03"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plannedEndDate").value("2026-08-03"));
        String assignmentId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/activities/" + activityId + "/assignments", """
                {"resourceId":"%s","quantity":1,"plannedWork":16,"workUnit":"EQUIPMENT_HOUR","operatingHoursPerDay":8}
                """.formatted(equipmentId)));
        postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/equipment-assignments/" + assignmentId + "/crew", """
                {"personnelResourceId":"%s","roleName":"Operator","quantity":1,"workingHoursPerDay":8}
                """.formatted(operatorId));
        String materialAssignmentId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/activities/" + activityId + "/assignments", """
                {"resourceId":"%s","quantity":1,"requiredQuantity":25,"wastePercentage":5}
                """.formatted(materialId)));
        mvc.perform(delete("/api/v1/projects/{p}/estimates/{e}/activities/{a}/assignments/{assignment}", projectId, estimateId, activityId, materialAssignmentId))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/projects/{p}/estimates/{e}/activities/{a}", projectId, estimateId, activityId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"A-1","name":"Seabed excavation","plannedDuration":4,"durationUnit":"DAY","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-04"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignments[0].plannedWork").value(32));
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/cost", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.equipmentCost").value(4000))
                .andExpect(jsonPath("$.personnelCost").value(800)).andExpect(jsonPath("$.fuelCost").value(640))
                .andExpect(jsonPath("$.totalCost").value(5440));
    }

    @Test
    void rejectsInvalidProjectDates() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"X","name":"Invalid","plannedStartDate":"2026-08-10","plannedEndDate":"2026-08-01","currencyCode":"TRY"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("End date cannot be before start date"));
    }
    private String postJson(String url, String json) throws Exception { return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); }
    private String id(String json) throws Exception { JsonNode node = mapper.readTree(json); assertThat(node.path("id").asText()).isNotBlank(); return node.path("id").asText(); }
}
