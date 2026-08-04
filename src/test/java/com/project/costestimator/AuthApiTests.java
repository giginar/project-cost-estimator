package com.project.costestimator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.project.costestimator.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.enabled=true", "app.mail.delivery-enabled=false"})
@AutoConfigureMockMvc
class AuthApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;

    @Test
    void enforcesRolePermissions() throws Exception {
        String engineer = login("engineer@example.com", "Engineer123!");
        String manager = login("manager@example.com", "Manager123!");
        String admin = login("admin@example.com", "Admin123!");
        assertThat(users.findByEmail("engineer@example.com").orElseThrow().getPasswordHash()).startsWith("$2").doesNotContain("Engineer123!");

        String projectsJson = mvc.perform(get("/api/v1/projects").header("Authorization", bearer(manager)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String projectId = mapper.readTree(projectsJson).get(0).path("id").asText();
        mvc.perform(put("/api/v1/projects/{id}", projectId).header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"MAR-001","name":"Marine Excavation — Phase 1","description":"Manager settings update","plannedStartDate":"2026-08-03","plannedEndDate":"2026-09-26","currencyCode":"USD","languageCode":"en","status":"DRAFT","usdTryRate":40,"eurTryRate":45}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.project.usdTryRate").value(40));
        mvc.perform(post("/api/v1/resources/personnel").header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"NO","name":"Forbidden","profession":"Test"}
                        """)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/resources/personnel").header("Authorization", bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"AUTH-PER","name":"Authorized","profession":"Test"}
                        """)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/projects").header("Authorization", bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users[0].passwordHash").doesNotExist());
    }

    @Test
    void registrationRequiresEmailVerification() throws Exception {
        String email = "new.manager@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"New Manager","email":"new.manager@example.com","password":"SecurePass123!"}
                """)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"new.manager@example.com","password":"SecurePass123!"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Verify your email before signing in"));

        String admin = login("admin@example.com", "Admin123!");
        String outbox = mvc.perform(get("/api/v1/admin/users/mail-outbox").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode entry = mapper.readTree(outbox).valueStream().filter(node -> email.equals(node.path("recipient").asText())).findFirst().orElseThrow();
        String url = entry.path("verificationUrl").asText();
        String token = url.substring(url.indexOf("verify=") + 7);
        mvc.perform(get("/api/v1/auth/verify").param("token", token)).andExpect(status().isOk());
        assertThat(login(email, "SecurePass123!")).isNotBlank();
    }

    @Test
    void passwordResetIsSingleUseAndInvalidatesSessions() throws Exception {
        String email = "reset.user@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Reset User","email":"reset.user@example.com","password":"OriginalPass123!"}
                """)).andExpect(status().isOk());
        String admin = login("admin@example.com", "Admin123!");
        String verificationToken = outboxToken(admin, email, "Verify", "verify=");
        mvc.perform(get("/api/v1/auth/verify").param("token", verificationToken)).andExpect(status().isOk());
        String oldSession = login(email, "OriginalPass123!");

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("If an active verified account exists for that email, a password reset link has been sent."));
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("If an active verified account exists for that email, a password reset link has been sent."));
        String resetToken = outboxToken(admin, email, "Reset", "reset=");
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","newPassword":"UpdatedPass123!"}
                """.formatted(resetToken))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects").header("Authorization", bearer(oldSession))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"OriginalPass123!\"}"))
                .andExpect(status().isBadRequest());
        assertThat(login(email, "UpdatedPass123!")).isNotBlank();
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","newPassword":"AnotherPass123!"}
                """.formatted(resetToken))).andExpect(status().isBadRequest());
    }

    private String login(String email, String password) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).path("accessToken").asText();
    }
    private String bearer(String token) { return "Bearer " + token; }
    private String outboxToken(String adminToken, String email, String subjectPrefix, String parameter) throws Exception {
        String outbox = mvc.perform(get("/api/v1/admin/users/mail-outbox").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode entry = mapper.readTree(outbox).valueStream()
                .filter(node -> email.equals(node.path("recipient").asText()) && node.path("subject").asText().startsWith(subjectPrefix))
                .findFirst().orElseThrow();
        String url = entry.path("verificationUrl").asText();
        return url.substring(url.indexOf(parameter) + parameter.length());
    }
}
