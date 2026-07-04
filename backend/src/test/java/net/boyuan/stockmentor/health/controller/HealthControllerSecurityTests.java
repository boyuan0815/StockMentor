package net.boyuan.stockmentor.health.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "STOCKMENTOR_CORS_ALLOWED_ORIGINS=http://localhost:8081,http://localhost:19006")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointsArePublicAndProtectedEndpointsStillRequireAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/health/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseHealthResponseDoesNotLeakTechnicalDetailsOrSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn();

        String body = result.getResponse().getContentAsString().toLowerCase();
        assertFalse(body.contains("jdbc"));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("username"));
        assertFalse(body.contains("hostname"));
        assertFalse(body.contains("exception"));
        assertFalse(body.contains("stack"));
        assertFalse(body.contains("openai"));
        assertFalse(body.contains("twelve"));
        assertFalse(body.contains("token"));
    }

    @Test
    void exactExpoWebCorsOriginFromEnvironmentStylePropertyIsAllowedWithoutWildcardCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:19006")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:19006"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }
}
