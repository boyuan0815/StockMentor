package net.boyuan.stockmentor.auth.controller;

import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserInvestmentProfileRepository profileRepository;
    @Autowired
    private UserBehaviorProfileRepository behaviorProfileRepository;
    @Autowired
    private StockAiSuggestionBatchRepository suggestionBatchRepository;
    @Autowired
    private StockAiSuggestionItemRepository suggestionItemRepository;
    @Autowired
    private PaperTradingAccountRepository paperTradingAccountRepository;
    @Autowired
    private PaperPositionRepository paperPositionRepository;
    @Autowired
    private PaperTradeTransactionRepository paperTradeTransactionRepository;
    @Autowired
    private UserWatchlistRepository watchlistRepository;
    @Autowired
    private StockAiExplanationRepository explanationRepository;
    @Autowired
    private StockAnalysisSnapshotRepository snapshotRepository;

    @Test
    void registerCreatesActiveBeginnerUserWithSafeResponseAndNoSideEffects() throws Exception {
        long profileCount = profileRepository.count();
        long behaviorCount = behaviorProfileRepository.count();
        long suggestionBatchCount = suggestionBatchRepository.count();
        long suggestionItemCount = suggestionItemRepository.count();
        long accountCount = paperTradingAccountRepository.count();
        long positionCount = paperPositionRepository.count();
        long transactionCount = paperTradeTransactionRepository.count();
        long watchlistCount = watchlistRepository.count();
        long explanationCount = explanationRepository.count();
        long snapshotCount = snapshotRepository.count();

        String suffix = uniqueSuffix();
        String email = "new-user-" + suffix + "@example.com";
        String username = "new.user_" + suffix + "-ok";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "  NEW-USER-%s@EXAMPLE.COM  ",
                                  "username": "  %s  ",
                                  "password": "ValidPass123",
                                  "confirmPassword": "ValidPass123"
                                }
                                """.formatted(suffix.toUpperCase(), username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("BEGINNER_INVESTOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.hasInvestmentProfile").value(false))
                .andExpect(jsonPath("$.mustCompleteOnboarding").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.jwt").doesNotExist());

        AppUser savedUser = appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertFalse(savedUser.getPasswordHash().equals("ValidPass123"));
        assertTrue(passwordEncoder.matches("ValidPass123", savedUser.getPasswordHash()));
        assertTrue(savedUser.getRole() == AppUserRole.BEGINNER_INVESTOR);
        assertTrue(savedUser.getStatus() == AppUserStatus.ACTIVE);
        assertFalse(Boolean.TRUE.equals(savedUser.getIsDeleted()));
        assertFalse(Boolean.TRUE.equals(savedUser.getOnboardingCompleted()));

        assertTrue(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(savedUser.getUserId()).isEmpty());
        assertTrue(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(savedUser.getUserId()).isEmpty());
        assertTrue(paperTradingAccountRepository.findByUserUserId(savedUser.getUserId()).isEmpty());
        assertTrue(paperPositionRepository.findByUserUserId(savedUser.getUserId()).isEmpty());
        assertTrue(paperTradeTransactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(savedUser.getUserId()).isEmpty());
        assertTrue(watchlistRepository.findByUserUserId(savedUser.getUserId()).isEmpty());

        assertTrue(profileRepository.count() == profileCount);
        assertTrue(behaviorProfileRepository.count() == behaviorCount);
        assertTrue(suggestionBatchRepository.count() == suggestionBatchCount);
        assertTrue(suggestionItemRepository.count() == suggestionItemCount);
        assertTrue(paperTradingAccountRepository.count() == accountCount);
        assertTrue(paperPositionRepository.count() == positionCount);
        assertTrue(paperTradeTransactionRepository.count() == transactionCount);
        assertTrue(watchlistRepository.count() == watchlistCount);
        assertTrue(explanationRepository.count() == explanationCount);
        assertTrue(snapshotRepository.count() == snapshotCount);
    }

    @Test
    void duplicateEmailAndUsernameReturnConflict() throws Exception {
        String suffix = uniqueSuffix();
        createUser("duplicate-" + suffix + "@example.com", "duplicate" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "DUPLICATE-%s@EXAMPLE.COM",
                                  "username": "another%s",
                                  "password": "ValidPass123",
                                  "confirmPassword": "ValidPass123"
                                }
                                """.formatted(suffix.toUpperCase(), suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unique-%s@example.com",
                                  "username": "DUPLICATE%s",
                                  "password": "ValidPass123",
                                  "confirmPassword": "ValidPass123"
                                }
                                """.formatted(suffix, suffix.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidRegistrationRequestsReturnBadRequest() throws Exception {
        registerBadRequest("""
                {
                  "email": "not-an-email",
                  "username": "validuser",
                  "password": "ValidPass123",
                  "confirmPassword": "ValidPass123"
                }
                """);
        registerBadRequest("""
                {
                  "email": "valid@example.com",
                  "username": "   ",
                  "password": "ValidPass123",
                  "confirmPassword": "ValidPass123"
                }
                """);
        registerBadRequest("""
                {
                  "email": "valid@example.com",
                  "username": "validuser",
                  "password": "ValidPass123",
                  "confirmPassword": "Different123"
                }
                """);
        registerBadRequest("""
                {
                  "email": "valid@example.com",
                  "username": "validuser",
                  "password": "short",
                  "confirmPassword": "short"
                }
                """);
        registerBadRequest("""
                {
                  "email": "valid@example.com",
                  "username": "validuser",
                  "password": "%s",
                  "confirmPassword": "%s"
                }
                """.formatted("a".repeat(73), "a".repeat(73)));
        registerBadRequest("""
                {
                  "email": "valid@example.com",
                  "username": "bad@example",
                  "password": "ValidPass123",
                  "confirmPassword": "ValidPass123"
                }
                """);
        registerBadRequest("""
                {
                  "email": "valid@example.com",
                  "username": "bad user",
                  "password": "ValidPass123",
                  "confirmPassword": "ValidPass123"
                }
                """);
    }

    @Test
    void loginReturnsSafeUserSummaryAndUpdatesLastLoginAt() throws Exception {
        String suffix = uniqueSuffix();
        AppUser beginner = createUser(
                "login-" + suffix + "@example.com",
                "login" + suffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.ACTIVE,
                false
        );

        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(beginner.getEmail().toUpperCase(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(beginner.getUserId()))
                .andExpect(jsonPath("$.email").value(beginner.getEmail()))
                .andExpect(jsonPath("$.role").value("BEGINNER_INVESTOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mustCompleteOnboarding").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.jwt").doesNotExist())
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());

        AppUser afterLogin = appUserRepository.findById(beginner.getUserId()).orElseThrow();
        assertTrue(afterLogin.getLastLoginAt() != null);
        assertNotEquals(beginner.getLastLoginAt(), afterLogin.getLastLoginAt());
    }

    @Test
    void loginSupportsEmailAndUsernameCaseInsensitiveLookups() throws Exception {
        String suffix = uniqueSuffix();
        AppUser user = createUser(
                "case-login-" + suffix + "@example.com",
                "CaseLogin" + suffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.ACTIVE,
                false
        );

        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(user.getEmail().toUpperCase(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(user.getUsername(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.username").value(user.getUsername()));
        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(user.getUsername().toUpperCase(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.username").value(user.getUsername()));
    }

    @Test
    void meReturnsSafeUserSummaryWithoutUpdatingLastLoginAt() throws Exception {
        String suffix = uniqueSuffix();
        AppUser user = createUser(
                "me-" + suffix + "@example.com",
                "me" + suffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.ACTIVE,
                false
        );
        LocalDateTime originalLoginAt = LocalDateTime.of(2026, 1, 1, 10, 15, 30);
        user.setLastLoginAt(originalLoginAt);
        appUserRepository.save(user);

        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic(user.getUsername(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser afterMe = appUserRepository.findById(user.getUserId()).orElseThrow();
        assertTrue(originalLoginAt.equals(afterMe.getLastLoginAt()));
    }

    @Test
    void invalidInactiveAndDeletedUsersCannotLogin() throws Exception {
        String inactiveSuffix = uniqueSuffix();
        String deletedSuffix = uniqueSuffix();
        AppUser inactive = createUser(
                "inactive-" + inactiveSuffix + "@example.com",
                "inactive" + inactiveSuffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.INACTIVE,
                false
        );
        AppUser deleted = createUser(
                "deleted-" + deletedSuffix + "@example.com",
                "deleted" + deletedSuffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.ACTIVE,
                true
        );

        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic("missing@example.com", "bad-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("sign-in details")));
        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(inactive.getEmail(), "password")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(deleted.getEmail(), "password")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginAndMeWithoutBasicAuthReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Authentication is required")));
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Authentication is required")));
    }

    @Test
    void forbiddenApiAccessReturnsJsonWithoutBasicChallenge() throws Exception {
        String suffix = uniqueSuffix();
        AppUser beginner = createUser(
                "forbidden-" + suffix + "@example.com",
                "forbidden" + suffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.ACTIVE,
                false
        );

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic(beginner.getEmail(), "password")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("does not have access")));
    }

    @Test
    void corsPreflightForConfiguredExpoOriginIsAllowedWithoutBasicAuth() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://10.157.40.167:8081")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://10.157.40.167:8081"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("OPTIONS")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
    }

    @Test
    void adminAndBeginnerLoginReturnCorrectRolesWithoutCreatingSideEffects() throws Exception {
        String adminSuffix = uniqueSuffix();
        String beginnerSuffix = uniqueSuffix();
        AppUser admin = createUser(
                "admin-login-" + adminSuffix + "@example.com",
                "adminlogin" + adminSuffix,
                AppUserRole.ADMIN,
                AppUserStatus.ACTIVE,
                false
        );
        AppUser beginner = createUser(
                "beginner-login-" + beginnerSuffix + "@example.com",
                "beginnerlogin" + beginnerSuffix,
                AppUserRole.BEGINNER_INVESTOR,
                AppUserStatus.ACTIVE,
                false
        );
        long profileCount = profileRepository.count();
        long behaviorCount = behaviorProfileRepository.count();
        long suggestionBatchCount = suggestionBatchRepository.count();
        long accountCount = paperTradingAccountRepository.count();
        long watchlistCount = watchlistRepository.count();

        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic(admin.getEmail(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustCompleteOnboarding").value(false));
        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic(beginner.getEmail(), "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BEGINNER_INVESTOR"))
                .andExpect(jsonPath("$.mustCompleteOnboarding").value(true));

        assertTrue(profileRepository.count() == profileCount);
        assertTrue(behaviorProfileRepository.count() == behaviorCount);
        assertTrue(suggestionBatchRepository.count() == suggestionBatchCount);
        assertTrue(paperTradingAccountRepository.count() == accountCount);
        assertTrue(watchlistRepository.count() == watchlistCount);
    }

    private void registerBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private AppUser createUser(
            String email,
            String username,
            AppUserRole role,
            AppUserStatus status,
            boolean deleted
    ) {
        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setEmail(email.toLowerCase());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setRole(role);
        user.setStatus(status);
        user.setIsDeleted(deleted);
        user.setOnboardingCompleted(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return appUserRepository.save(user);
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
