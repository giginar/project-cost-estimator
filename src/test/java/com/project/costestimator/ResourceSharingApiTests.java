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
class ResourceSharingApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void keepsLocalResourcesInTheirProjectAndAllowsOwnerToShareThem() throws Exception {
        String projectA = project("SCOPE-A");
        String projectB = project("SCOPE-B");
        String activityB = activity(projectB);
        String estimateB = mapper.readTree(mvc.perform(get("/api/v1/projects/{id}", projectB)).andReturn().getResponse().getContentAsString()).path("estimates").get(0).path("id").asText();

        String localResource = id(mvc.perform(post("/api/v1/resources/materials")
                        .param("projectId", projectA).param("shared", "false")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"LOCAL-A","name":"Local aggregate","materialType":"AGGREGATE","defaultUnit":"TON"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shared").value(false))
                .andExpect(jsonPath("$.ownerProjectId").value(projectA)).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/resources").param("projectId", projectA)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", localResource).exists());
        mvc.perform(get("/api/v1/resources").param("projectId", projectB)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", localResource).doesNotExist());
        mvc.perform(post("/api/v1/projects/{p}/estimates/{e}/activities/{a}/assignments", projectB, estimateB, activityB)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"resourceId":"%s","quantity":1,"requiredQuantity":10}
                        """.formatted(localResource)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Resource is not available to this project"));

        mvc.perform(put("/api/v1/resources/{id}/sharing", localResource).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"shared\":true}".formatted(projectB)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Only the owning project can change resource sharing"));
        mvc.perform(put("/api/v1/resources/{id}/sharing", localResource).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"shared\":true}".formatted(projectA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shared").value(true));
        mvc.perform(get("/api/v1/resources").param("projectId", projectB)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", localResource).exists());
        mvc.perform(post("/api/v1/projects/{p}/estimates/{e}/activities/{a}/assignments", projectB, estimateB, activityB)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"resourceId":"%s","quantity":1,"requiredQuantity":10}
                """.formatted(localResource)))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/resources/{id}", localResource).param("projectId", projectA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Resource is in use; remove its activity, crew, and project staff assignments first"));

        String unusedResource = id(mvc.perform(post("/api/v1/resources/materials")
                        .param("projectId", projectA).param("shared", "false")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"UNUSED-A","name":"Unused material","materialType":"OTHER","defaultUnit":"PIECE"}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(delete("/api/v1/resources/{id}", unusedResource).param("projectId", projectB))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Only the owning project can delete this resource"));
        mvc.perform(delete("/api/v1/resources/{id}", unusedResource).param("projectId", projectA)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/resources/{id}", unusedResource)).andExpect(status().isNotFound());
    }

    private String project(String code) throws Exception {
        String projectId = mapper.readTree(postJson("/api/v1/projects", """
                {"code":"%s","name":"%s","plannedStartDate":"2026-01-01","plannedEndDate":"2026-01-31","currencyCode":"USD"}
                """.formatted(code, code))).path("project").path("id").asText();
        activity(projectId);
        return projectId;
    }
    private String activity(String projectId) throws Exception {
        var detail = mapper.readTree(mvc.perform(get("/api/v1/projects/{id}", projectId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        if (!detail.path("estimates").isEmpty()) return detail.path("estimates").get(0).path("wbsItems").get(0).path("activities").get(0).path("id").asText();
        String estimate = id(postJson("/api/v1/projects/" + projectId + "/estimates", "{\"name\":\"Baseline\"}"));
        String wbs = id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimate + "/wbs-items", "{\"code\":\"1\",\"name\":\"Works\"}"));
        return id(postJson("/api/v1/projects/" + projectId + "/estimates/" + estimate + "/wbs-items/" + wbs + "/activities", """
                {"code":"A-1","name":"Work","plannedDuration":1,"durationUnit":"DAY","plannedStartDate":"2026-01-01","plannedEndDate":"2026-01-01"}
                """));
    }
    private String postJson(String path, String json) throws Exception { return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); }
    private String id(String json) throws Exception { return mapper.readTree(json).path("id").asText(); }
}
