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
class StockAiSuggestionControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpUser() {
        appUserRepository.findByEmailOrUsername("auth-user@example.com", "auth-user")
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    AppUser user = new AppUser();
                    user.setEmail("auth-user@example.com");
                    user.setUsername("auth-user");
                    user.setPasswordHash(passwordEncoder.encode("password"));
                    user.setRole(AppUserRole.BEGINNER_INVESTOR);
                    user.setStatus(AppUserStatus.ACTIVE);
                    user.setIsDeleted(false);
                    user.setOnboardingCompleted(false);
                    user.setCreatedAt(now);
                    user.setUpdatedAt(now);
                    return appUserRepository.save(user);
                });
    }

    @Test
    void getSuggestionsRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/stocks/ai-suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSuggestionsAllowsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/stocks/ai-suggestions")
                        .with(httpBasic("auth-user@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedStocks").isArray())
                .andExpect(jsonPath("$.remainingStocks").isArray());
    }
}
