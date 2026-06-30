package net.boyuan.stockmentor.userprofile.service.impl;

import net.boyuan.stockmentor.ai.dto.SuggestionTriggerResult;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionTriggerService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.userprofile.dto.OnboardingAnswerRequest;
import net.boyuan.stockmentor.userprofile.dto.OnboardingQuestionResponse;
import net.boyuan.stockmentor.userprofile.dto.OnboardingSubmitRequest;
import net.boyuan.stockmentor.userprofile.dto.UserProfileResponse;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import net.boyuan.stockmentor.userprofile.model.ExperienceLevel;
import net.boyuan.stockmentor.userprofile.model.InvestmentGoal;
import net.boyuan.stockmentor.userprofile.model.PreferredHorizon;
import net.boyuan.stockmentor.userprofile.model.PreferredVolatility;
import net.boyuan.stockmentor.userprofile.model.ProfileSource;
import net.boyuan.stockmentor.userprofile.model.RiskTolerance;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTests {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserInvestmentProfileRepository profileRepository;
    @Mock
    private PaperTradingAccountRepository paperTradingAccountRepository;
    @Mock
    private UserBehaviorProfileService behaviorProfileService;
    @Mock
    private StockAiSuggestionTriggerService stockAiSuggestionTriggerService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private UserProfileServiceImpl service;
    private AppUser user;
    private List<Runnable> backgroundTasks;

    @BeforeEach
    void setUp() {
        backgroundTasks = new ArrayList<>();
        TaskExecutor backgroundTaskExecutor = backgroundTasks::add;
        service = new UserProfileServiceImpl(
                currentUserService,
                appUserRepository,
                profileRepository,
                paperTradingAccountRepository,
                behaviorProfileService,
                stockAiSuggestionTriggerService,
                transactionManager,
                backgroundTaskExecutor
        );
        user = user(1L, false);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(behaviorSummary());
        lenient().when(paperTradingAccountRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        lenient().when(stockAiSuggestionTriggerService.handleOnboardingCompleted(any(AppUser.class)))
                .thenReturn(SuggestionTriggerResult.success(StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED, 101L, "SUCCESS", "created"));
        lenient().when(stockAiSuggestionTriggerService.handleProfileRetaken(any(AppUser.class), any(UserInvestmentProfile.class)))
                .thenReturn(SuggestionTriggerResult.success(StockAiSuggestionTriggerReason.RETAKE_QUIZ, 102L, "SUCCESS", "created"));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void questionsListUsesApprovedStableIdsAndNoScoringFields() {
        OnboardingQuestionResponse response = service.getOnboardingQuestions();

        assertEquals(8, response.questions().size());
        assertEquals(List.of(
                        "risk_reaction",
                        "volatility_comfort",
                        "investment_goal",
                        "experience_level",
                        "investment_horizon",
                        "concentration_comfort",
                        "loss_tolerance",
                        "guidance_preference"
                ),
                response.questions().stream().map(question -> question.questionId()).toList());
        assertTrue(response.questions().stream().allMatch(question -> question.required() && !question.options().isEmpty()));
        assertEquals("risk_reaction_sell_reduce", response.questions().get(0).options().get(0).optionId());
        assertEquals("I would sell or reduce it because I want to avoid further loss.", response.questions().get(0).options().get(0).label());
        assertEquals("I would sell or reduce it because I want to avoid further loss.", response.questions().get(0).options().get(0).description());
        verify(currentUserService).getCurrentUser();
    }

    @Test
    void completeOnboardingCreatesMaxPlusOneProfileAndRegistersAfterCommitTrigger() {
        TransactionSynchronizationManager.initSynchronization();
        when(profileRepository.findMaxProfileVersionByUserId(1L)).thenReturn(2);
        when(profileRepository.save(any(UserInvestmentProfile.class))).thenAnswer(invocation -> {
            UserInvestmentProfile profile = invocation.getArgument(0);
            profile.setProfileId(10L);
            return profile;
        });

        UserProfileResponse response = service.completeOnboarding(validRequest());

        assertTrue(user.getOnboardingCompleted());
        assertEquals(3, response.investmentProfile().profileVersion());
        assertEquals("ONBOARDING", response.investmentProfile().profileSource());
        assertEquals("MODERATE", response.investmentProfile().riskTolerance());
        assertEquals("BALANCED", response.investmentProfile().investmentGoal());
        assertEquals("BASIC", response.investmentProfile().experienceLevel());
        assertEquals("MEDIUM", response.investmentProfile().preferredVolatility());
        assertEquals("MEDIUM_TERM", response.investmentProfile().preferredHorizon());
        assertEquals(55, response.investmentProfile().riskScore());
        assertEquals(55, response.investmentProfile().goalScore());
        assertEquals(45, response.investmentProfile().experienceScore());

        ArgumentCaptor<UserInvestmentProfile> profileCaptor = ArgumentCaptor.forClass(UserInvestmentProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        UserInvestmentProfile savedProfile = profileCaptor.getValue();
        assertNull(savedProfile.getBehaviorRiskScore());
        assertNull(savedProfile.getBehaviorStyle());
        assertNull(savedProfile.getBehaviorConfidence());
        verify(stockAiSuggestionTriggerService, never()).handleOnboardingCompleted(any());

        triggerAfterCommit();

        verify(stockAiSuggestionTriggerService, never()).handleOnboardingCompleted(any());
        assertEquals(1, backgroundTasks.size());
        runBackgroundTasks();

        verify(stockAiSuggestionTriggerService).handleOnboardingCompleted(user);
        ArgumentCaptor<TransactionDefinition> definitionCaptor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definitionCaptor.getValue().getPropagationBehavior());
    }

    @Test
    void completeOnboardingRejectsAlreadyCompletedUser() {
        user.setOnboardingCompleted(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.completeOnboarding(validRequest())
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("Onboarding has already been completed. Use retake instead.", exception.getReason());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void completeOnboardingAllowsExistingProfileRowsWhenUserNotCompleted() {
        TransactionSynchronizationManager.initSynchronization();
        when(profileRepository.findMaxProfileVersionByUserId(1L)).thenReturn(5);
        when(profileRepository.save(any(UserInvestmentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = service.completeOnboarding(validRequest());

        assertEquals(6, response.investmentProfile().profileVersion());
        assertEquals("ONBOARDING", response.investmentProfile().profileSource());
    }

    @Test
    void retakeCreatesNewProfileWithoutModifyingOldProfile() {
        TransactionSynchronizationManager.initSynchronization();
        user.setOnboardingCompleted(true);
        UserInvestmentProfile oldProfile = profile(user, 1);
        LocalDateTime oldUpdatedAt = oldProfile.getUpdatedAt();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(oldProfile));
        when(profileRepository.findMaxProfileVersionByUserId(1L)).thenReturn(1);
        when(profileRepository.save(any(UserInvestmentProfile.class))).thenAnswer(invocation -> {
            UserInvestmentProfile profile = invocation.getArgument(0);
            profile.setProfileId(20L);
            return profile;
        });

        UserProfileResponse response = service.retakeOnboarding(aggressiveRetakeRequest());

        assertTrue(user.getOnboardingCompleted());
        assertEquals("RETAKE_QUIZ", response.investmentProfile().profileSource());
        assertEquals(2, response.investmentProfile().profileVersion());
        assertEquals("AGGRESSIVE", response.investmentProfile().riskTolerance());
        assertEquals("GROWTH", response.investmentProfile().investmentGoal());
        assertEquals("INTERMEDIATE", response.investmentProfile().experienceLevel());
        assertEquals("HIGH", response.investmentProfile().preferredVolatility());
        assertEquals("LONG_TERM", response.investmentProfile().preferredHorizon());
        assertEquals(oldUpdatedAt, oldProfile.getUpdatedAt());

        triggerAfterCommit();

        assertEquals(1, backgroundTasks.size());
        runBackgroundTasks();

        verify(stockAiSuggestionTriggerService).handleProfileRetaken(eq(user), any(UserInvestmentProfile.class));
    }

    @Test
    void retakeRequiresCompletedUserAndLatestProfile() {
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.retakeOnboarding(validRequest())
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("Complete onboarding before retaking the quiz.", exception.getReason());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void validationRejectsNullAndEmptyAnswers() {
        assertValidationMessage(new OnboardingSubmitRequest(null), "Onboarding answers are required");
        assertValidationMessage(new OnboardingSubmitRequest(List.of()), "Onboarding answers must not be empty");
    }

    @Test
    void validationRejectsMissingDuplicateUnknownAndWrongQuestionOptionPairs() {
        List<OnboardingAnswerRequest> missing = new ArrayList<>(validAnswers());
        missing.remove(0);
        assertValidationMessage(new OnboardingSubmitRequest(missing), "Missing onboarding answer for question: risk_reaction");

        List<OnboardingAnswerRequest> duplicate = new ArrayList<>(validAnswers());
        duplicate.add(new OnboardingAnswerRequest("risk_reaction", "risk_reaction_buy_more"));
        assertValidationMessage(new OnboardingSubmitRequest(duplicate), "Duplicate onboarding answer for question: risk_reaction");

        List<OnboardingAnswerRequest> unknownQuestion = replaceAnswer("bad_question", "risk_reaction_wait_review");
        assertValidationMessage(new OnboardingSubmitRequest(unknownQuestion), "Unknown onboarding question: bad_question");

        List<OnboardingAnswerRequest> unknownOption = replaceAnswer("risk_reaction", "bad_option");
        assertValidationMessage(new OnboardingSubmitRequest(unknownOption), "Unknown onboarding option: bad_option");

        List<OnboardingAnswerRequest> wrongPair = replaceAnswer("risk_reaction", "volatility_medium");
        assertValidationMessage(new OnboardingSubmitRequest(wrongPair), "Option volatility_medium does not belong to question: risk_reaction");
    }

    @Test
    void afterCommitTriggerExceptionIsCaughtInsideCallback() {
        TransactionSynchronizationManager.initSynchronization();
        when(profileRepository.findMaxProfileVersionByUserId(1L)).thenReturn(0);
        when(profileRepository.save(any(UserInvestmentProfile.class))).thenAnswer(invocation -> {
            UserInvestmentProfile profile = invocation.getArgument(0);
            profile.setProfileId(30L);
            return profile;
        });
        doThrow(new IllegalStateException("trigger failed")).when(stockAiSuggestionTriggerService).handleOnboardingCompleted(user);

        service.completeOnboarding(validRequest());

        assertDoesNotThrow(this::triggerAfterCommit);
        assertTrue(user.getOnboardingCompleted());
        assertEquals(1, backgroundTasks.size());
        assertDoesNotThrow(this::runBackgroundTasks);
        verify(transactionManager).rollback(any(TransactionStatus.class));
    }

    @Test
    void afterCommitLogsCompletedWithoutBatchResultWithoutThrowing() {
        TransactionSynchronizationManager.initSynchronization();
        when(profileRepository.findMaxProfileVersionByUserId(1L)).thenReturn(0);
        when(profileRepository.save(any(UserInvestmentProfile.class))).thenAnswer(invocation -> {
            UserInvestmentProfile profile = invocation.getArgument(0);
            profile.setProfileId(40L);
            return profile;
        });
        when(stockAiSuggestionTriggerService.handleOnboardingCompleted(user))
                .thenReturn(SuggestionTriggerResult.success(
                        StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED,
                        null,
                        null,
                        "No usable stock analysis data is available for suggestions yet."
                ));

        service.completeOnboarding(validRequest());

        assertDoesNotThrow(this::triggerAfterCommit);
        assertEquals(1, backgroundTasks.size());
        assertDoesNotThrow(this::runBackgroundTasks);
    }

    @Test
    void currentProfileDowngradesBehaviorSummaryAfterPortfolioResetBoundary() {
        user.setOnboardingCompleted(true);
        UserInvestmentProfile profile = profile(user, 2);
        LocalDateTime behaviorUpdatedAt = LocalDateTime.of(2026, 6, 29, 9, 0);
        PaperTradingAccount account = new PaperTradingAccount();
        account.setLastResetAt(behaviorUpdatedAt.plusMinutes(5));

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L))
                .thenReturn(Optional.of(profile));
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L))
                .thenReturn(behaviorSummary(BehaviorConfidence.MEDIUM, behaviorUpdatedAt));
        when(paperTradingAccountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));

        UserProfileResponse response = service.getCurrentUserProfile();

        assertEquals("LOW", response.behaviorSummary().behaviorConfidence());
        assertEquals("INSUFFICIENT_DATA", response.behaviorSummary().behaviorStyle());
        assertTrue(response.behaviorSummary().sourceNote().contains("Portfolio was reset"));
        assertNull(response.behaviorSummary().updatedAt());
    }

    @Test
    void postServiceMethodsAreTransactional() throws Exception {
        assertNotNull(UserProfileServiceImpl.class
                .getMethod("completeOnboarding", OnboardingSubmitRequest.class)
                .getAnnotation(Transactional.class));
        assertNotNull(UserProfileServiceImpl.class
                .getMethod("retakeOnboarding", OnboardingSubmitRequest.class)
                .getAnnotation(Transactional.class));
    }

    private void assertValidationMessage(OnboardingSubmitRequest request, String message) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.completeOnboarding(request)
        );
        assertEquals(message, exception.getMessage());
    }

    private void triggerAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    private void runBackgroundTasks() {
        List<Runnable> tasks = new ArrayList<>(backgroundTasks);
        backgroundTasks.clear();
        tasks.forEach(Runnable::run);
    }

    private OnboardingSubmitRequest validRequest() {
        return new OnboardingSubmitRequest(validAnswers());
    }

    private List<OnboardingAnswerRequest> validAnswers() {
        return List.of(
                new OnboardingAnswerRequest("risk_reaction", "risk_reaction_wait_review"),
                new OnboardingAnswerRequest("volatility_comfort", "volatility_medium"),
                new OnboardingAnswerRequest("investment_goal", "goal_balanced"),
                new OnboardingAnswerRequest("experience_level", "experience_basic"),
                new OnboardingAnswerRequest("investment_horizon", "horizon_medium"),
                new OnboardingAnswerRequest("concentration_comfort", "concentration_balanced"),
                new OnboardingAnswerRequest("loss_tolerance", "loss_tolerance_medium"),
                new OnboardingAnswerRequest("guidance_preference", "guidance_balanced_compare")
        );
    }

    private OnboardingSubmitRequest aggressiveRetakeRequest() {
        return new OnboardingSubmitRequest(List.of(
                new OnboardingAnswerRequest("risk_reaction", "risk_reaction_buy_more"),
                new OnboardingAnswerRequest("volatility_comfort", "volatility_high"),
                new OnboardingAnswerRequest("investment_goal", "goal_growth"),
                new OnboardingAnswerRequest("experience_level", "experience_intermediate"),
                new OnboardingAnswerRequest("investment_horizon", "horizon_long"),
                new OnboardingAnswerRequest("concentration_comfort", "concentration_focused"),
                new OnboardingAnswerRequest("loss_tolerance", "loss_tolerance_high"),
                new OnboardingAnswerRequest("guidance_preference", "guidance_growth_opportunities")
        ));
    }

    private List<OnboardingAnswerRequest> replaceAnswer(String questionId, String optionId) {
        List<OnboardingAnswerRequest> answers = new ArrayList<>(validAnswers());
        answers.set(0, new OnboardingAnswerRequest(questionId, optionId));
        return answers;
    }

    private AppUser user(Long userId, boolean onboardingCompleted) {
        AppUser appUser = new AppUser();
        appUser.setUserId(userId);
        appUser.setEmail("user" + userId + "@example.com");
        appUser.setUsername("user" + userId);
        appUser.setRole(AppUserRole.BEGINNER_INVESTOR);
        appUser.setStatus(AppUserStatus.ACTIVE);
        appUser.setIsDeleted(false);
        appUser.setOnboardingCompleted(onboardingCompleted);
        appUser.setCreatedAt(LocalDateTime.now().minusDays(1));
        appUser.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return appUser;
    }

    private UserInvestmentProfile profile(AppUser user, int version) {
        UserInvestmentProfile profile = new UserInvestmentProfile();
        profile.setProfileId(5L);
        profile.setUser(user);
        profile.setRiskTolerance(RiskTolerance.MODERATE);
        profile.setInvestmentGoal(InvestmentGoal.BALANCED);
        profile.setExperienceLevel(ExperienceLevel.BASIC);
        profile.setPreferredVolatility(PreferredVolatility.MEDIUM);
        profile.setPreferredHorizon(PreferredHorizon.MEDIUM_TERM);
        profile.setRiskScore(55);
        profile.setGoalScore(55);
        profile.setExperienceScore(45);
        profile.setProfileSource(ProfileSource.ONBOARDING);
        profile.setProfileVersion(version);
        profile.setCreatedAt(LocalDateTime.now().minusDays(2));
        profile.setUpdatedAt(LocalDateTime.now().minusDays(2));
        return profile;
    }

    private BehaviorSummaryForSuggestion behaviorSummary() {
        return behaviorSummary(BehaviorConfidence.LOW, null);
    }

    private BehaviorSummaryForSuggestion behaviorSummary(BehaviorConfidence confidence, LocalDateTime updatedAt) {
        return new BehaviorSummaryForSuggestion(
                null,
                null,
                null,
                confidence == BehaviorConfidence.LOW ? null : 60,
                confidence == BehaviorConfidence.LOW ? UserBehaviorStyle.INSUFFICIENT_DATA : UserBehaviorStyle.BALANCED,
                confidence,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "No paper-trading behavior profile has been calculated yet.",
                updatedAt,
                "Behavior profile is unavailable; no paper-trading transaction source exists yet."
        );
    }
}
