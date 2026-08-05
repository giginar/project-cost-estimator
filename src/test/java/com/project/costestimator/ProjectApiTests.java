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

@SpringBootTest(properties = "app.security.enabled=false")
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
        String equipmentRateId = id(postJson("/api/v1/resources/" + equipmentId + "/cost-components", """
                {"category":"RENTAL","name":"Daily rental","calculationBasis":"PER_DAY","unitPrice":1000,"unit":"DAY","currencyCode":"TRY"}
                """));
        postJson("/api/v1/resources/" + equipmentId + "/cost-components", """
                {"category":"FUEL","name":"Diesel price","calculationBasis":"PER_UNIT","unitPrice":10,"unit":"LITER","currencyCode":"TRY"}
                """);
        postJson("/api/v1/resources/" + equipmentId + "/fuel-consumptions", """
                {"fuelType":"DIESEL","consumptionPerHour":2,"standbyConsumptionPerHour":1,"consumptionUnit":"LITER"}
                """);
        postJson("/api/v1/resources/" + operatorId + "/cost-components", """
                {"category":"SALARY","name":"Hourly wage","calculationBasis":"PER_HOUR","unitPrice":25,"unit":"HOUR","currencyCode":"TRY"}
                """);
        String projectId = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"MAR-01","name":"Marine excavation","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-10","currencyCode":"TRY"}
                """)).path("project").path("id").asText();
        mvc.perform(put("/api/v1/projects/{project}/calendar", projectId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Seven-day test calendar","workingDaysPerWeek":7,"workingHoursPerDay":8,"shifts":[{"name":"Day","startTime":"08:00:00","endTime":"16:00:00","paidHours":8}]}
                """))
                .andExpect(status().isOk());
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
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/cost-report", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total.totalCost").value(5440))
                .andExpect(jsonPath("$.projectLevel.totalCost").value(0))
                .andExpect(jsonPath("$.wbsItems[0].code").value("1"))
                .andExpect(jsonPath("$.wbsItems[0].costs.totalCost").value(5440))
                .andExpect(jsonPath("$.wbsItems[0].activities[0].code").value("A-1"))
                .andExpect(jsonPath("$.wbsItems[0].activities[0].costs.fuelCost").value(640));
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/resource-rates", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.sourceCostComponentId == '%s')]", equipmentRateId).exists());
        mvc.perform(put("/api/v1/projects/{p}/estimates/{e}/activities/{a}/assignments/{assignment}", projectId, estimateId, activityId, assignmentId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"resourceId":"%s","quantity":1,"plannedWork":32,"workUnit":"EQUIPMENT_HOUR","utilizationRate":50,"operatingHoursPerDay":4,"standbyHoursPerDay":4}
                        """.formatted(equipmentId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operatingHoursPerDay").value(4))
                .andExpect(jsonPath("$.standbyHoursPerDay").value(4));
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/cost", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fuelCost").value(320))
                .andExpect(jsonPath("$.personnelCost").value(400)).andExpect(jsonPath("$.totalCost").value(4720));
        mvc.perform(put("/api/v1/projects/{p}/estimates/{e}/activities/{a}/assignments/{assignment}", projectId, estimateId, activityId, assignmentId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"resourceId":"%s","quantity":1,"plannedWork":32,"workUnit":"EQUIPMENT_HOUR","utilizationRate":100,"operatingHoursPerDay":8,"standbyHoursPerDay":0}
                        """.formatted(equipmentId)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/{p}/estimates/{e}/resource-rates/{rate}", projectId, estimateId, equipmentRateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":1100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unitPrice").value(1100));
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/cost", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.equipmentCost").value(4400))
                .andExpect(jsonPath("$.totalCost").value(5840));
        mvc.perform(put("/api/v1/projects/{p}/estimates/{e}/resource-rates/{rate}", projectId, estimateId, equipmentRateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":1000}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/{p}", projectId).contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"MAR-01","name":"Marine excavation","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-10","currencyCode":"TRY","languageCode":"tr","status":"ACTIVE","usdTryRate":35,"eurTryRate":38}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.project.currency").value("TRY"))
                .andExpect(jsonPath("$.project.languageCode").value("tr")).andExpect(jsonPath("$.project.usdTryRate").value(35))
                .andExpect(jsonPath("$.project.eurTryRate").value(38)).andExpect(jsonPath("$.project.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/projects/{p}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.project.usdTryRate").value(35)).andExpect(jsonPath("$.project.eurTryRate").value(38));
        mvc.perform(put("/api/v1/projects/{p}", projectId).contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"MAR-01","name":"Marine excavation","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-10","currencyCode":"EUR","languageCode":"tr","status":"ACTIVE"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.project.currency").value("EUR"))
                .andExpect(jsonPath("$.project.usdTryRate").value(35)).andExpect(jsonPath("$.project.eurTryRate").value(38));
        mvc.perform(get("/api/v1/resources/{id}", equipmentId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.costs[0].unitPrice").value(1000))
                .andExpect(jsonPath("$.costs[0].currencyCode").value("TRY"));
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/cost", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.equipmentCost").value(105.2632))
                .andExpect(jsonPath("$.personnelCost").value(21.0528)).andExpect(jsonPath("$.fuelCost").value(16.8448))
                .andExpect(jsonPath("$.totalCost").value(143.1608));
    }

    @Test
    void rejectsInvalidProjectDates() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"X","name":"Invalid","plannedStartDate":"2026-08-10","plannedEndDate":"2026-08-01","currencyCode":"TRY"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("End date cannot be before start date"));
    }

    @Test
    void seedsLargePortProjectForProjectSwitching() throws Exception {
        JsonNode projects = mapper.readTree(mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode seededProject = projects.valueStream()
                .filter(project -> "PORT-2027".equals(project.path("code").asText()))
                .findFirst().orElseThrow();

        JsonNode detail = mapper.readTree(mvc.perform(get("/api/v1/projects/{id}", seededProject.path("id").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode wbsItems = detail.path("estimates").get(0).path("wbsItems");
        int activityCount = wbsItems.valueStream().mapToInt(wbs -> wbs.path("activities").size()).sum();

        assertThat(seededProject.path("name").asText()).isEqualTo("Aegean Deepwater Port Expansion");
        assertThat(wbsItems.size()).isEqualTo(6);
        assertThat(activityCount).isEqualTo(15);
    }

    @Test
    void persistsCreatedProjectEstimateAndWbsForSubsequentReads() throws Exception {
        String projectId = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"PERSIST-01","name":"Persistence check","plannedStartDate":"2028-01-01","plannedEndDate":"2028-03-31","currencyCode":"USD"}
                """)).path("project").path("id").asText();
        String estimateId = id(postJson("/api/v1/projects/" + projectId + "/estimates", "{\"name\":\"Baseline\"}"));
        String wbsId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items", "{\"code\":\"1\",\"name\":\"Initial WBS\"}"));

        mvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.code").value("PERSIST-01"))
                .andExpect(jsonPath("$.estimates[0].id").value(estimateId))
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].id").value(wbsId))
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].name").value("Initial WBS"));
    }

    private String postJson(String url, String json) throws Exception { return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); }
    private String id(String json) throws Exception { JsonNode node = mapper.readTree(json); assertThat(node.path("id").asText()).isNotBlank(); return node.path("id").asText(); }
}
