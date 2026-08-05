package com.project.costestimator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.security.enabled=false")
@AutoConfigureMockMvc
class PhaseOneApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void connectsBoqProductivityDependenciesCalendarAndTraceability() throws Exception {
        String projectId = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"PH1-01","name":"Phase one project","plannedStartDate":"2026-08-07","plannedEndDate":"2026-09-30","currencyCode":"USD"}
                """)).path("project").path("id").asText();
        mvc.perform(put("/api/v1/projects/{project}/calendar", projectId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Two-shift calendar","workingDaysPerWeek":5,"workingHoursPerDay":16,"shifts":[
                  {"name":"Day","startTime":"08:00:00","endTime":"16:00:00","paidHours":8},
                  {"name":"Night","startTime":"16:00:00","endTime":"00:00:00","paidHours":8}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingDaysPerWeek").value(5))
                .andExpect(jsonPath("$.workingHoursPerDay").value(16)).andExpect(jsonPath("$.shifts.length()").value(2));
        String estimateId = id(postJson("/api/v1/projects/" + projectId + "/estimates", "{\"name\":\"Baseline\"}"));
        String wbsId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items", "{\"code\":\"1\",\"name\":\"Earthworks\"}"));
        String firstId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items/" + wbsId + "/activities", """
                {"code":"A-1","name":"Excavate","type":"WORK","plannedQuantity":100,"quantityUnit":"CUBIC_METER","plannedStartDate":"2026-08-07","plannedEndDate":"2026-08-07","dailyProductionRate":30,"autoSchedule":true}
                """));
        String secondId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items/" + wbsId + "/activities", """
                {"code":"A-2","name":"Haul","type":"WORK","plannedQuantity":50,"quantityUnit":"CUBIC_METER","plannedStartDate":"2026-08-07","plannedEndDate":"2026-08-07","dailyProductionRate":25,"autoSchedule":true}
                """));
        mvc.perform(get("/api/v1/projects/{project}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[0].plannedDuration").value(4))
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[0].plannedEndDate").value("2026-08-12"));
        postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/activities/" + secondId + "/dependencies", """
                {"predecessorActivityId":"%s","type":"FINISH_TO_START","lagDays":1}
                """.formatted(firstId));
        mvc.perform(get("/api/v1/projects/{project}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[1].plannedStartDate").value("2026-08-14"))
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[1].plannedEndDate").value("2026-08-17"))
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[1].dependencies[0].predecessorCode").value("A-1"));
        mvc.perform(post("/api/v1/projects/{project}/estimates/{estimate}/activities/{activity}/dependencies", projectId, estimateId, firstId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"predecessorActivityId":"%s","type":"FINISH_TO_START","lagDays":0}
                        """.formatted(secondId)))
                .andExpect(status().isBadRequest());
        String boqId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/boq-items", """
                {"code":"BOQ-001","description":"Excavation and haul","unit":"CUBIC_METER","quantity":50,"unitPrice":100,"currencyCode":"USD","wbsId":"%s","activityId":"%s"}
                """.formatted(wbsId, secondId)));
        mvc.perform(get("/api/v1/projects/{project}/estimates/{estimate}/boq-traceability", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(1)).andExpect(jsonPath("$.linkedItemCount").value(1))
                .andExpect(jsonPath("$.unlinkedItemCount").value(0)).andExpect(jsonPath("$.totalBoqValue").value(5000))
                .andExpect(jsonPath("$.items[0].wbsCode").value("1")).andExpect(jsonPath("$.items[0].activityCode").value("A-2"));
        mvc.perform(put("/api/v1/projects/{project}/estimates/{estimate}/boq-items/{boq}", projectId, estimateId, boqId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"BOQ-001","description":"Excavation and haul","unit":"CUBIC_METER","quantity":75,"unitPrice":100,"currencyCode":"USD","wbsId":"%s","activityId":"%s"}
                        """.formatted(wbsId, secondId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(7500));
        mvc.perform(get("/api/v1/projects/{project}", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[1].plannedQuantity").value(75))
                .andExpect(jsonPath("$.estimates[0].wbsItems[0].activities[1].plannedDuration").value(3));
        mvc.perform(delete("/api/v1/projects/{project}/estimates/{estimate}/boq-items/{boq}", projectId, estimateId, boqId)).andExpect(status().isNoContent());
    }

    private String postJson(String path, String json) throws Exception { return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); }
    private String id(String json) throws Exception { return mapper.readTree(json).path("id").asText(); }
}
