package net.boyuan.stockmentor.ai.controller;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiSuggestionControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpUsers() {
        createUser("admin-v6@example.com", "admin-v6", "password", AppUserRole.ADMIN);
        createUser("beginner-v6@example.com", "beginner-v6", "password", AppUserRole.BEGINNER_INVESTOR);
    }

    @Test
    void adminMonitoringRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions/batches")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminMonitoringRejectsBeginnerEvenWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions/batches")
                        .with(httpBasic("beginner-v6@example.com", "password"))
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminMonitoringRejectsAdminWithoutAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions/batches")
                        .with(httpBasic("admin-v6@example.com", "password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin token"));
    }

    @Test
    void adminMonitoringRejectsAdminWithWrongAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions/batches")
                        .with(httpBasic("admin-v6@example.com", "password"))
                        .header("X-Admin-Token", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin token"));
    }

    @Test
    void adminMonitoringAllowsAdminWithValidAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/ai-suggestions/batches")
                        .with(httpBasic("admin-v6@example.com", "password"))
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    private void createUser(String email, String username, String password, AppUserRole role) {
        appUserRepository.findByEmailOrUsername(email, username)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    AppUser user = new AppUser();
                    user.setEmail(email);
                    user.setUsername(username);
                    user.setPasswordHash(passwordEncoder.encode(password));
                    user.setRole(role);
                    user.setStatus(AppUserStatus.ACTIVE);
                    user.setIsDeleted(false);
                    user.setOnboardingCompleted(false);
                    user.setCreatedAt(now);
                    user.setUpdatedAt(now);
                    return appUserRepository.save(user);
                });
    }
}
