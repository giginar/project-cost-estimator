package com.project.costestimator;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.security.enabled=false")
@AutoConfigureMockMvc
class NewFeatureApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void usesCentralFuelPriceAndReportsMonthlyCashFlowByCostCode() throws Exception {
        Context context = createContext("NEW-FEATURE-1", "2026-01-01", "2026-01-31");
        mvc.perform(post("/api/v1/projects/{project}/settings/unit-prices", context.projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"DSL","name":"Project diesel","fuelType":"DIESEL","unit":"LITER","unitPrice":2.5,"active":true}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.unitPrice").value(2.5));

        String wbsId = id(postJson("/api/v1/projects/" + context.projectId + "/estimates/" + context.estimateId + "/wbs-items", """
                {"code":"1","name":"Imported works"}
                """));
        String activityId = id(postJson("/api/v1/projects/" + context.projectId + "/estimates/" + context.estimateId + "/wbs-items/" + wbsId + "/activities", """
                {"code":"A-1","name":"Diesel operation","plannedDuration":2,"durationUnit":"DAY","plannedStartDate":"2026-01-01","plannedEndDate":"2026-01-02"}
                """));
        String equipmentId = id(postJson("/api/v1/resources/equipment?shared=false&projectId=" + context.projectId, """
                {"code":"EQ-CENTRAL","name":"Central price equipment","equipmentType":"EXCAVATOR","owned":false}
                """));
        postJson("/api/v1/resources/" + equipmentId + "/fuel-consumptions", """
                {"fuelType":"DIESEL","consumptionPerHour":2,"standbyConsumptionPerHour":0,"consumptionUnit":"LITER"}
                """);
        postJson("/api/v1/projects/" + context.projectId + "/estimates/" + context.estimateId + "/activities/" + activityId + "/assignments", """
                {"resourceId":"%s","quantity":1,"plannedWork":16,"workUnit":"EQUIPMENT_HOUR","utilizationRate":100,"operatingHoursPerDay":8,"standbyHoursPerDay":0}
                """.formatted(equipmentId));
        postJson("/api/v1/projects/" + context.projectId + "/estimates/" + context.estimateId + "/boq-items", """
                {"code":"BOQ-1","description":"Diesel operation","unit":"PIECE","quantity":10,"unitPrice":10,"currencyCode":"USD","wbsId":"%s","activityId":"%s"}
                """.formatted(wbsId, activityId));

        mvc.perform(get("/api/v1/projects/{project}/estimates/{estimate}/cost-report", context.projectId, context.estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total.fuelCost").value(80));
        String costCodesJson = mvc.perform(get("/api/v1/projects/{project}/settings/cost-codes", context.projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.type == 'FUEL')].code").value("FUEL"))
                .andReturn().getResponse().getContentAsString();
        JsonNode fuelCode = mapper.readTree(costCodesJson).valueStream()
                .filter(code -> "FUEL".equals(code.path("type").asText())).findFirst().orElseThrow();
        mvc.perform(put("/api/v1/projects/{project}/settings/cost-codes/{code}", context.projectId, fuelCode.path("id").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"ENE","name":"Energy","type":"FUEL","active":true}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("ENE"));
        mvc.perform(get("/api/v1/projects/{project}/estimates/{estimate}/cash-flow", context.projectId, context.estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalIncome").value(100))
                .andExpect(jsonPath("$.totalExpense").value(80))
                .andExpect(jsonPath("$.netCashFlow").value(20))
                .andExpect(jsonPath("$.revenueBasis").value("BOQ_VALUE"))
                .andExpect(jsonPath("$.timingBasis").value("PLANNED_ACTIVITY_PERIOD"))
                .andExpect(jsonPath("$.months[0].costsByCode[?(@.code == 'ENE')].amount").value(80.0));
    }

    @Test
    void previewsAndImportsFixedFiveColumnBoqSpreadsheet() throws Exception {
        Context context = createContext("NEW-FEATURE-2", "2026-02-01", "2026-03-31");
        byte[] workbook = boqWorkbook();
        MockMultipartFile file = new MockMultipartFile(
                "file", "boq.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        mvc.perform(multipart("/api/v1/projects/{project}/estimates/{estimate}/boq-import", context.projectId, context.estimateId)
                        .file(file).param("preview", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.itemCount").value(2)).andExpect(jsonPath("$.createdWbsCount").value(1))
                .andExpect(jsonPath("$.issues.length()").value(0));

        mvc.perform(multipart("/api/v1/projects/{project}/estimates/{estimate}/boq-import", context.projectId, context.estimateId)
                        .file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preview").value(false))
                .andExpect(jsonPath("$.itemCount").value(2)).andExpect(jsonPath("$.createdWbsCount").value(1));
        mvc.perform(get("/api/v1/projects/{project}/estimates/{estimate}/boq-traceability", context.projectId, context.estimateId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.items[0].code").value("1.1"))
                .andExpect(jsonPath("$.items[1].unit").value("CUBIC_METER"));
    }

    private Context createContext(String code, String start, String end) throws Exception {
        JsonNode project = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"%s","name":"New features","plannedStartDate":"%s","plannedEndDate":"%s","currencyCode":"USD"}
                """.formatted(code, start, end)));
        String projectId = project.path("project").path("id").asText();
        String estimateId = id(postJson("/api/v1/projects/" + projectId + "/estimates", "{\"name\":\"Baseline\"}"));
        return new Context(projectId, estimateId);
    }

    private byte[] boqWorkbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("BOQ");
            Object[][] values = {
                    {"Item No", "Description", "Unit", "Quantity", "Row Type"},
                    {"1", "Earthworks", "", "", "HEADER"},
                    {"1.1", "Excavation", "m3", 125d, "BOQ_ITEM"},
                    {"1.2", "Backfill", "m3", 80d, "BOQ_ITEM"}
            };
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                for (int column = 0; column < values[rowIndex].length; column++) {
                    Object value = values[rowIndex][column];
                    if (value instanceof Number number) row.createCell(column).setCellValue(number.doubleValue());
                    else row.createCell(column).setCellValue(String.valueOf(value));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String id(String json) throws Exception {
        String id = mapper.readTree(json).path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }

    private record Context(String projectId, String estimateId) {}
}
