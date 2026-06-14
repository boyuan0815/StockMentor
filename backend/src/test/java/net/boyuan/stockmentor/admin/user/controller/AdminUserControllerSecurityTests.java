package net.boyuan.stockmentor.admin.user.controller;

import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.papertrading.model.PaperTradingAccountStatus;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import net.boyuan.stockmentor.userprofile.model.ExperienceLevel;
import net.boyuan.stockmentor.userprofile.model.InvestmentGoal;
import net.boyuan.stockmentor.userprofile.model.PreferredHorizon;
import net.boyuan.stockmentor.userprofile.model.PreferredVolatility;
import net.boyuan.stockmentor.userprofile.model.ProfileSource;
import net.boyuan.stockmentor.userprofile.model.RiskTolerance;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerSecurityTests {
    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final String PASSWORD = "password";

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
    private PaperTradingAccountRepository paperTradingAccountRepository;
    @Autowired
    private PaperPositionRepository paperPositionRepository;
    @Autowired
    private PaperTradeTransactionRepository paperTradeTransactionRepository;
    @Autowired
    private UserWatchlistRepository watchlistRepository;
    @Autowired
    private StockAiSuggestionBatchRepository suggestionBatchRepository;
    @Autowired
    private StockAiSuggestionItemRepository suggestionItemRepository;
    @Autowired
    private StockAiExplanationRepository explanationRepository;
    @Autowired
    private StockAnalysisSnapshotRepository snapshotRepository;

    @Test
    void adminUserEndpointsEnforceBasicAuthRoleAndAdminToken() throws Exception {
        String suffix = uniqueSuffix();
        AppUser admin = createUser("admin-sec-" + suffix + "@example.com", "adminsec" + suffix, AppUserRole.ADMIN, AppUserStatus.ACTIVE, false);
        AppUser beginner = createUser("beginner-sec-" + suffix + "@example.com", "beginnersec" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);

        mockMvc.perform(get("/api/admin/users")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic(beginner.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic(admin.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin token"));

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid admin token"));

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void listUsersSupportsPaginationFiltersAndDefaultNonDeletedUsers() throws Exception {
        String suffix = uniqueSuffix();
        AppUser admin = createUser("admin-list-" + suffix + "@example.com", "adminlist" + suffix, AppUserRole.ADMIN, AppUserStatus.ACTIVE, false);
        AppUser active = createUser("alpha-list-" + suffix + "@example.com", "AlphaList" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);
        AppUser inactive = createUser("beta-list-" + suffix + "@example.com", "BetaList" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.INACTIVE, false);
        createUser("deleted-list-" + suffix + "@example.com", "DeletedList" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, true);

        mockMvc.perform(get("/api/admin/users")
                        .param("search", "LIST-" + suffix.toUpperCase())
                        .param("page", "0")
                        .param("size", "999")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(3));

        mockMvc.perform(get("/api/admin/users")
                        .param("email", "ALPHA-LIST-" + suffix.toUpperCase())
                        .param("username", "alphalist" + suffix.toLowerCase())
                        .param("role", "BEGINNER_INVESTOR")
                        .param("status", "ACTIVE")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(active.getUserId()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/admin/users")
                        .param("search", "betaLIST" + suffix.toUpperCase())
                        .param("status", "INACTIVE")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(inactive.getUserId()));

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "-1")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users")
                        .param("size", "0")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users")
                        .param("role", "OWNER")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users")
                        .param("status", "LOCKED")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userDetailReturnsSafeReadOnlySummariesWithoutCreatingPaperTradingAccount() throws Exception {
        String suffix = uniqueSuffix();
        AppUser admin = createUser("admin-detail-" + suffix + "@example.com", "admindetail" + suffix, AppUserRole.ADMIN, AppUserStatus.ACTIVE, false);
        AppUser user = createUser("detail-" + suffix + "@example.com", "detail" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);
        createInvestmentProfile(user, 1, RiskTolerance.CONSERVATIVE);
        createInvestmentProfile(user, 2, RiskTolerance.MODERATE);
        createBehaviorProfile(user);
        createPaperTradingData(user);

        long accountCount = paperTradingAccountRepository.count();

        mockMvc.perform(get("/api/admin/users/{userId}", user.getUserId())
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.latestInvestmentProfile.profileVersion").value(2))
                .andExpect(jsonPath("$.latestInvestmentProfile.riskTolerance").value("MODERATE"))
                .andExpect(jsonPath("$.behaviorSummary.behaviorStyle").value("BALANCED"))
                .andExpect(jsonPath("$.paperTradingSummary.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.paperTradingSummary.positionCount").value(1))
                .andExpect(jsonPath("$.paperTradingSummary.transactionCount").value(1));

        assertEquals(accountCount, paperTradingAccountRepository.count());

        AppUser noAccountUser = createUser("no-account-" + suffix + "@example.com", "noaccount" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);
        mockMvc.perform(get("/api/admin/users/{userId}", noAccountUser.getUserId())
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paperTradingSummary").value(nullValue()));
        assertTrue(paperTradingAccountRepository.findByUserUserId(noAccountUser.getUserId()).isEmpty());

        mockMvc.perform(get("/api/admin/users/{userId}", 999999999L)
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanDisableAndReEnableNormalUserWithoutDeletingLinkedRecords() throws Exception {
        String suffix = uniqueSuffix();
        AppUser admin = createUser("admin-status-" + suffix + "@example.com", "adminstatus" + suffix, AppUserRole.ADMIN, AppUserStatus.ACTIVE, false);
        AppUser user = createUser("status-" + suffix + "@example.com", "status" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);
        createInvestmentProfile(user, 1, RiskTolerance.MODERATE);
        createBehaviorProfile(user);
        createPaperTradingData(user);
        createWatchlist(user);

        long profileCount = profileRepository.count();
        long behaviorCount = behaviorProfileRepository.count();
        long suggestionBatchCount = suggestionBatchRepository.count();
        long suggestionItemCount = suggestionItemRepository.count();
        long explanationCount = explanationRepository.count();
        long snapshotCount = snapshotRepository.count();
        long watchlistCount = watchlistRepository.count();
        long accountCount = paperTradingAccountRepository.count();
        long positionCount = paperPositionRepository.count();
        long transactionCount = paperTradeTransactionRepository.count();

        mockMvc.perform(patch("/api/admin/users/{userId}/status", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("INACTIVE"));

        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/users/{userId}/status", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/admin/users/{userId}/status", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));

        assertEquals(profileCount, profileRepository.count());
        assertEquals(behaviorCount, behaviorProfileRepository.count());
        assertEquals(suggestionBatchCount, suggestionBatchRepository.count());
        assertEquals(suggestionItemCount, suggestionItemRepository.count());
        assertEquals(explanationCount, explanationRepository.count());
        assertEquals(snapshotCount, snapshotRepository.count());
        assertEquals(watchlistCount, watchlistRepository.count());
        assertEquals(accountCount, paperTradingAccountRepository.count());
        assertEquals(positionCount, paperPositionRepository.count());
        assertEquals(transactionCount, paperTradeTransactionRepository.count());
    }

    @Test
    void statusUpdateRejectsSuspendedDeletedSelfDisableAndLastActiveAdminDisable() throws Exception {
        String suffix = uniqueSuffix();
        AppUser admin = createUser("admin-guard-" + suffix + "@example.com", "adminguard" + suffix, AppUserRole.ADMIN, AppUserStatus.ACTIVE, false);
        AppUser user = createUser("guard-" + suffix + "@example.com", "guard" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, false);
        AppUser deleted = createUser("guard-deleted-" + suffix + "@example.com", "guarddeleted" + suffix, AppUserRole.BEGINNER_INVESTOR, AppUserStatus.ACTIVE, true);

        mockMvc.perform(patch("/api/admin/users/{userId}/status", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/users/{userId}/status", deleted.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/admin/users/{userId}/status", admin.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}")
                        .with(httpBasic(admin.getEmail(), PASSWORD))
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    void us001AndUs002RemainAvailable() throws Exception {
        String suffix = uniqueSuffix();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "us003-reg-%s@example.com",
                                  "username": "us003reg%s",
                                  "password": "ValidPass123",
                                  "confirmPassword": "ValidPass123"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .with(httpBasic("us003-reg-" + suffix + "@example.com", "ValidPass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BEGINNER_INVESTOR"));

        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic("us003reg" + suffix, "ValidPass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("us003reg" + suffix));
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
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        user.setIsDeleted(deleted);
        user.setOnboardingCompleted(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return appUserRepository.save(user);
    }

    private void createInvestmentProfile(AppUser user, int version, RiskTolerance riskTolerance) {
        LocalDateTime now = LocalDateTime.now().plusSeconds(version);
        UserInvestmentProfile profile = new UserInvestmentProfile();
        profile.setUser(user);
        profile.setRiskTolerance(riskTolerance);
        profile.setInvestmentGoal(InvestmentGoal.GROWTH);
        profile.setExperienceLevel(ExperienceLevel.BEGINNER);
        profile.setPreferredVolatility(PreferredVolatility.MEDIUM);
        profile.setPreferredHorizon(PreferredHorizon.MEDIUM_TERM);
        profile.setRiskScore(55 + version);
        profile.setGoalScore(70);
        profile.setExperienceScore(20);
        profile.setBehaviorConfidence(BehaviorConfidence.LOW);
        profile.setProfileSource(ProfileSource.ONBOARDING);
        profile.setProfileVersion(version);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profileRepository.save(profile);
    }

    private void createBehaviorProfile(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        UserBehaviorProfile behavior = new UserBehaviorProfile();
        behavior.setUser(user);
        behavior.setBehaviorStyle(UserBehaviorStyle.BALANCED);
        behavior.setBehaviorConfidence(BehaviorConfidence.MEDIUM);
        behavior.setBehaviorRiskScore(62);
        behavior.setBehaviorSummaryText("Stored behavior summary");
        behavior.setCreatedAt(now);
        behavior.setUpdatedAt(now);
        behaviorProfileRepository.save(behavior);
    }

    private void createPaperTradingData(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        PaperTradingAccount account = new PaperTradingAccount();
        account.setUser(user);
        account.setCashBalance(new BigDecimal("99000.0000"));
        account.setStartingCash(new BigDecimal("100000.0000"));
        account.setStatus(PaperTradingAccountStatus.ACTIVE);
        account.setCurrentSessionNumber(1);
        account.setLastResetAt(now.minusDays(1));
        account.setCreatedAt(now.minusDays(2));
        account.setUpdatedAt(now);
        paperTradingAccountRepository.save(account);

        PaperPosition position = new PaperPosition();
        position.setUser(user);
        position.setSymbol("AAPL");
        position.setQuantity(2);
        position.setAverageCost(new BigDecimal("100.0000"));
        position.setTotalCost(new BigDecimal("200.0000"));
        position.setRealizedPl(BigDecimal.ZERO);
        position.setCreatedAt(now);
        position.setUpdatedAt(now);
        paperPositionRepository.save(position);

        PaperTradeTransaction transaction = new PaperTradeTransaction();
        transaction.setUser(user);
        transaction.setSymbol("AAPL");
        transaction.setSide(PaperTradeSide.SELL);
        transaction.setQuantity(1);
        transaction.setExecutionPrice(new BigDecimal("120.0000"));
        transaction.setGrossAmount(new BigDecimal("120.0000"));
        transaction.setFee(new BigDecimal("1.0000"));
        transaction.setNetAmount(new BigDecimal("119.0000"));
        transaction.setRealizedProfitLoss(new BigDecimal("19.0000"));
        transaction.setCashBalanceAfter(new BigDecimal("99119.0000"));
        transaction.setIsCurrentSession(true);
        transaction.setSessionNumber(1);
        transaction.setExecutedAt(now);
        paperTradeTransactionRepository.save(transaction);
    }

    private void createWatchlist(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        UserWatchlist watchlist = new UserWatchlist();
        watchlist.setUser(user);
        watchlist.setSymbol("NVDA");
        watchlist.setSource(WatchlistSource.MANUAL);
        watchlist.setCreatedAt(now);
        watchlist.setUpdatedAt(now);
        watchlistRepository.save(watchlist);
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
