package net.boyuan.stockmentor.userprofile.controller;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.ExperienceLevel;
import net.boyuan.stockmentor.userprofile.model.InvestmentGoal;
import net.boyuan.stockmentor.userprofile.model.PreferredHorizon;
import net.boyuan.stockmentor.userprofile.model.PreferredVolatility;
import net.boyuan.stockmentor.userprofile.model.ProfileSource;
import net.boyuan.stockmentor.userprofile.model.RiskTolerance;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private UserInvestmentProfileRepository profileRepository;
    @Autowired
    private UserBehaviorProfileRepository behaviorProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser authUser;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        behaviorProfileRepository.deleteAll();
        authUser = ensureUser("us004-auth@example.com", "us004-auth", false);
        otherUser = ensureUser("us004-other@example.com", "us004-other", true);
    }

    @Test
    void unauthenticatedUserProfileEndpointsRejectRequests() throws Exception {
        mockMvc.perform(get("/api/user/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/onboarding/questions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/user/onboarding")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/user/onboarding/retake")).andExpect(status().isUnauthorized());
    }

    @Test
    void questionsEndpointReturnsApprovedQuestionsWithoutScoringFields() throws Exception {
        mockMvc.perform(get("/api/user/onboarding/questions")
                        .with(httpBasic("us004-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(8))
                .andExpect(jsonPath("$.questions[0].questionId").value("risk_reaction"))
                .andExpect(jsonPath("$.questions[0].text").value("If a stock in your paper portfolio drops by 15%, what would you most likely do?"))
                .andExpect(jsonPath("$.questions[0].required").value(true))
                .andExpect(jsonPath("$.questions[0].options[0].optionId").value("risk_reaction_sell_reduce"))
                .andExpect(jsonPath("$.questions[0].options[0].label").value("I would sell or reduce it because I want to avoid further loss."))
                .andExpect(jsonPath("$.questions[0].options[0].description").value("I would sell or reduce it because I want to avoid further loss."))
                .andExpect(jsonPath("$.questions[0].options[0].riskScore").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[0].score").doesNotExist());
    }

    @Test
    void profileEndpointReturnsCurrentUserLatestProfileAndSafeBehaviorFallback() throws Exception {
        saveProfile(authUser, 1, RiskTolerance.CONSERVATIVE);
        saveProfile(authUser, 2, RiskTolerance.MODERATE);
        saveProfile(otherUser, 3, RiskTolerance.AGGRESSIVE);

        mockMvc.perform(get("/api/user/profile")
                        .with(httpBasic("us004-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(authUser.getUserId()))
                .andExpect(jsonPath("$.email").value("us004-auth@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.investmentProfile.profileVersion").value(2))
                .andExpect(jsonPath("$.investmentProfile.riskTolerance").value("MODERATE"))
                .andExpect(jsonPath("$.investmentProfile.profileSource").value("ONBOARDING"))
                .andExpect(jsonPath("$.behaviorSummary.behaviorConfidence").value("LOW"))
                .andExpect(jsonPath("$.behaviorSummary.behaviorSummaryText").isNotEmpty())
                .andExpect(jsonPath("$.rawPrompt").doesNotExist())
                .andExpect(jsonPath("$.rawOpenAiResponse").doesNotExist())
                .andExpect(jsonPath("$.tokenUsage").doesNotExist());
    }

    @Test
    void validationErrorsReturnBadRequestShape() throws Exception {
        mockMvc.perform(post("/api/user/onboarding")
                        .with(httpBasic("us004-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Onboarding answers are required"));
    }

    @Test
    void completedUserFirstOnboardingReturnsConflictShape() throws Exception {
        authUser.setOnboardingCompleted(true);
        appUserRepository.save(authUser);

        mockMvc.perform(post("/api/user/onboarding")
                        .with(httpBasic("us004-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Onboarding has already been completed. Use retake instead."));
    }

    @Test
    void retakeBeforeOnboardingReturnsConflictShape() throws Exception {
        mockMvc.perform(post("/api/user/onboarding/retake")
                        .with(httpBasic("us004-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Complete onboarding before retaking the quiz."));
    }

    private AppUser ensureUser(String email, String username, boolean onboardingCompleted) {
        AppUser user = appUserRepository.findByEmailOrUsername(email, username)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    AppUser created = new AppUser();
                    created.setEmail(email);
                    created.setUsername(username);
                    created.setPasswordHash(passwordEncoder.encode("password"));
                    created.setRole(AppUserRole.BEGINNER_INVESTOR);
                    created.setStatus(AppUserStatus.ACTIVE);
                    created.setIsDeleted(false);
                    created.setCreatedAt(now);
                    return created;
                });
        user.setOnboardingCompleted(onboardingCompleted);
        user.setUpdatedAt(LocalDateTime.now());
        return appUserRepository.save(user);
    }

    private void saveProfile(AppUser user, int version, RiskTolerance riskTolerance) {
        UserInvestmentProfile profile = new UserInvestmentProfile();
        profile.setUser(user);
        profile.setRiskTolerance(riskTolerance);
        profile.setInvestmentGoal(InvestmentGoal.BALANCED);
        profile.setExperienceLevel(ExperienceLevel.BASIC);
        profile.setPreferredVolatility(PreferredVolatility.MEDIUM);
        profile.setPreferredHorizon(PreferredHorizon.MEDIUM_TERM);
        profile.setRiskScore(55);
        profile.setGoalScore(55);
        profile.setExperienceScore(45);
        profile.setProfileSource(ProfileSource.ONBOARDING);
        profile.setProfileVersion(version);
        profile.setCreatedAt(LocalDateTime.now().minusDays(version));
        profile.setUpdatedAt(LocalDateTime.now().minusDays(version));
        profileRepository.save(profile);
    }

    private String validRequestJson() {
        return """
                {
                  "answers": [
                    {"questionId":"risk_reaction","optionId":"risk_reaction_wait_review"},
                    {"questionId":"volatility_comfort","optionId":"volatility_medium"},
                    {"questionId":"investment_goal","optionId":"goal_balanced"},
                    {"questionId":"experience_level","optionId":"experience_basic"},
                    {"questionId":"investment_horizon","optionId":"horizon_medium"},
                    {"questionId":"concentration_comfort","optionId":"concentration_balanced"},
                    {"questionId":"loss_tolerance","optionId":"loss_tolerance_medium"},
                    {"questionId":"guidance_preference","optionId":"guidance_balanced_compare"}
                  ]
                }
                """;
    }
}
