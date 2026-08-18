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
class PhaseThreeFourApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void editsResourceEconomicsAndBuildsSequentialSalesPrice() throws Exception {
        String equipmentId = id(postJson("/api/v1/resources/equipment", """
                {"code":"PH34-EQ","name":"Owned crane","equipmentType":"CRANE","owned":false}
                """));
        mvc.perform(put("/api/v1/resources/{id}/equipment-economics", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"owned":true,"acquisitionCost":120000,"residualValue":24000,"usefulLifeMonths":48,
                         "maintenanceRatePercentage":6,"insuranceRatePercentage":1.2,"currencyCode":"USD"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyDepreciation").value(2000))
                .andExpect(jsonPath("$.monthlyMaintenance").value(600)).andExpect(jsonPath("$.monthlyInsurance").value(120));
        mvc.perform(get("/api/v1/resources/{id}", equipmentId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentEconomics.owned").value(true))
                .andExpect(jsonPath("$.costs.length()").value(3))
                .andExpect(jsonPath("$.costs[0].generated").value(true));

        String materialId = id(postJson("/api/v1/resources/materials", """
                {"code":"PH34-MAT","name":"Armor rock","materialType":"ROCK","defaultUnit":"TON"}
                """));
        String costId = id(postJson("/api/v1/resources/" + materialId + "/cost-components", """
                {"category":"MATERIAL","name":"Supplier quote","calculationBasis":"PER_UNIT","unitPrice":100,
                 "unit":"TON","currencyCode":"USD","validFrom":"2026-01-01","validTo":"2027-12-31"}
                """));
        mvc.perform(put("/api/v1/resources/{resource}/cost-components/{cost}", materialId, costId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"category":"MATERIAL","name":"Updated supplier quote","calculationBasis":"PER_UNIT","unitPrice":120,
                         "unit":"TON","currencyCode":"USD","validFrom":"2026-01-01","validTo":"2027-12-31"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unitPrice").value(120));
        mvc.perform(put("/api/v1/resources/{id}/material-procurement", materialId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"supplier":"Quarry A","leadTimeDays":14,"minimumOrderQuantity":25,"defaultWastePercentage":10}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supplier").value("Quarry A"))
                .andExpect(jsonPath("$.defaultWastePercentage").value(10));

        String projectId = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"PH34","name":"Commercial test","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-31","currencyCode":"USD"}
                """)).path("project").path("id").asText();
        String estimateId = id(postJson("/api/v1/projects/" + projectId + "/estimates", "{\"name\":\"Bid\"}"));
        String wbsId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items", "{\"code\":\"1\",\"name\":\"Works\"}"));
        String activityId = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/wbs-items/" + wbsId + "/activities", """
                {"code":"A-1","name":"Place rock","plannedDuration":1,"durationUnit":"DAY","plannedStartDate":"2026-08-01","plannedEndDate":"2026-08-01"}
                """));
        postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/activities/" + activityId + "/assignments", """
                {"resourceId":"%s","quantity":1,"requiredQuantity":10}
                """.formatted(materialId));
        postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/boq-items", """
                {"code":"BOQ-1","description":"Place rock","unit":"TON","quantity":15,"unitPrice":100,"currencyCode":"USD","wbsId":"%s","activityId":"%s"}
                """.formatted(wbsId, activityId));
        postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/pricing-rules", """
                {"type":"OVERHEAD","name":"Overhead","percentage":10,"base":"ESTIMATED_COST","sequence":1,"active":true}
                """);
        postJson("/api/v1/projects/" + projectId + "/estimates/" + estimateId + "/pricing-rules", """
                {"type":"PROFIT","name":"Profit","percentage":20,"base":"RUNNING_TOTAL","sequence":2,"active":true}
                """);

        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/pricing-summary", projectId, estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estimatedCost").value(1320))
                .andExpect(jsonPath("$.nonProfitAdders").value(132)).andExpect(jsonPath("$.profit").value(290.4))
                .andExpect(jsonPath("$.salesPrice").value(1742.4)).andExpect(jsonPath("$.boqValue").value(1500))
                .andExpect(jsonPath("$.netProfit").value(180))
                .andExpect(jsonPath("$.profitMarginPercentage").value(12))
                .andExpect(jsonPath("$.targetProfitMarginPercentage").value(16.6667))
                .andExpect(jsonPath("$.boqVariance").value(-242.4))
                .andExpect(jsonPath("$.revenueBasis").value("BOQ_VALUE"))
                .andExpect(jsonPath("$.lines.length()").value(2));
        mvc.perform(get("/api/v1/projects/{p}/estimates/{e}/cash-flow", projectId, estimateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1500))
                .andExpect(jsonPath("$.totalExpense").value(1320))
                .andExpect(jsonPath("$.netCashFlow").value(180))
                .andExpect(jsonPath("$.revenueBasis").value("BOQ_VALUE"))
                .andExpect(jsonPath("$.timingBasis").value("PLANNED_ACTIVITY_PERIOD"));
    }

    private String postJson(String path, String json) throws Exception { return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); }
    private String id(String json) throws Exception { return mapper.readTree(json).path("id").asText(); }
}
