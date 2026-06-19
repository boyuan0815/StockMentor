package net.boyuan.stockmentor.userprofile.service.impl;

import net.boyuan.stockmentor.ai.dto.SuggestionTriggerResult;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionTriggerService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.userprofile.dto.BehaviorProfileSummaryResponse;
import net.boyuan.stockmentor.userprofile.dto.InvestmentProfileResponse;
import net.boyuan.stockmentor.userprofile.dto.OnboardingAnswerRequest;
import net.boyuan.stockmentor.userprofile.dto.OnboardingOptionDto;
import net.boyuan.stockmentor.userprofile.dto.OnboardingQuestionDto;
import net.boyuan.stockmentor.userprofile.dto.OnboardingQuestionResponse;
import net.boyuan.stockmentor.userprofile.dto.OnboardingSubmitRequest;
import net.boyuan.stockmentor.userprofile.dto.UserProfileResponse;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.ExperienceLevel;
import net.boyuan.stockmentor.userprofile.model.InvestmentGoal;
import net.boyuan.stockmentor.userprofile.model.PreferredHorizon;
import net.boyuan.stockmentor.userprofile.model.PreferredVolatility;
import net.boyuan.stockmentor.userprofile.model.ProfileSource;
import net.boyuan.stockmentor.userprofile.model.RiskTolerance;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class UserProfileServiceImpl implements net.boyuan.stockmentor.userprofile.service.UserProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserProfileServiceImpl.class);
    private static final String ONBOARDING_ALREADY_COMPLETED = "Onboarding has already been completed. Use retake instead.";
    private static final String COMPLETE_ONBOARDING_BEFORE_RETAKE = "Complete onboarding before retaking the quiz.";

    private static final List<QuestionDefinition> QUESTIONS = List.of(
            riskQuestion(
                    "risk_reaction",
                    "If a stock in your paper portfolio drops by 15%, what would you most likely do?",
                    List.of(
                            riskOption("risk_reaction_sell_reduce", "I would sell or reduce it because I want to avoid further loss.", 25),
                            riskOption("risk_reaction_wait_review", "I would wait and review the situation before deciding.", 55),
                            riskOption("risk_reaction_buy_more", "I may buy more if I still believe in the stock.", 85)
                    )
            ),
            volatilityQuestion(),
            goalQuestion(),
            experienceQuestion(),
            horizonQuestion(),
            riskQuestion(
                    "concentration_comfort",
                    "How would you prefer to spread your virtual money?",
                    List.of(
                            riskOption("concentration_diversified", "Spread across several safer stocks.", 25),
                            riskOption("concentration_balanced", "Some spread, but I am okay focusing on a few stocks.", 55),
                            riskOption("concentration_focused", "Focus more money on stocks I strongly believe in.", 85)
                    )
            ),
            riskQuestion(
                    "loss_tolerance",
                    "What temporary loss level would make you uncomfortable?",
                    List.of(
                            riskOption("loss_tolerance_low", "Around 5% or less.", 25),
                            riskOption("loss_tolerance_medium", "Around 10%.", 55),
                            riskOption("loss_tolerance_high", "15% or more is acceptable in a simulation.", 85)
                    )
            ),
            riskQuestion(
                    "guidance_preference",
                    "What kind of suggestion would help you most as a beginner?",
                    List.of(
                            riskOption("guidance_safer_explained", "Safer choices with simple explanations.", 25),
                            riskOption("guidance_balanced_compare", "Balanced suggestions comparing risk and growth.", 55),
                            riskOption("guidance_growth_opportunities", "Growth-focused suggestions with risk warnings.", 85)
                    )
            )
    );
    private static final Map<String, QuestionDefinition> QUESTION_BY_ID = QUESTIONS.stream()
            .collect(Collectors.toUnmodifiableMap(QuestionDefinition::questionId, question -> question));

    private final CurrentUserService currentUserService;
    private final AppUserRepository appUserRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final UserBehaviorProfileService behaviorProfileService;
    private final StockAiSuggestionTriggerService stockAiSuggestionTriggerService;
    private final PlatformTransactionManager transactionManager;
    private final TaskExecutor backgroundTaskExecutor;

    public UserProfileServiceImpl(
            CurrentUserService currentUserService,
            AppUserRepository appUserRepository,
            UserInvestmentProfileRepository profileRepository,
            UserBehaviorProfileService behaviorProfileService,
            StockAiSuggestionTriggerService stockAiSuggestionTriggerService,
            PlatformTransactionManager transactionManager,
            @Qualifier("stockMentorBackgroundTaskExecutor") TaskExecutor backgroundTaskExecutor
    ) {
        this.currentUserService = currentUserService;
        this.appUserRepository = appUserRepository;
        this.profileRepository = profileRepository;
        this.behaviorProfileService = behaviorProfileService;
        this.stockAiSuggestionTriggerService = stockAiSuggestionTriggerService;
        this.transactionManager = transactionManager;
        this.backgroundTaskExecutor = backgroundTaskExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        AppUser user = currentUserService.getCurrentUser();
        UserInvestmentProfile profile = profileRepository
                .findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId())
                .orElse(null);
        return toUserProfileResponse(user, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingQuestionResponse getOnboardingQuestions() {
        currentUserService.getCurrentUser();
        return new OnboardingQuestionResponse(QUESTIONS.stream()
                .map(QuestionDefinition::toResponse)
                .toList());
    }

    @Override
    @Transactional
    public UserProfileResponse completeOnboarding(OnboardingSubmitRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        if (Boolean.TRUE.equals(user.getOnboardingCompleted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ONBOARDING_ALREADY_COMPLETED);
        }

        ScoredAnswers scoredAnswers = validateAndScore(request);
        UserInvestmentProfile savedProfile = saveProfile(user, scoredAnswers, ProfileSource.ONBOARDING);
        LocalDateTime now = LocalDateTime.now();
        user.setOnboardingCompleted(true);
        user.setUpdatedAt(now);
        AppUser savedUser = appUserRepository.save(user);
        registerAfterCommitTrigger(() -> stockAiSuggestionTriggerService.handleOnboardingCompleted(savedUser), savedUser, savedProfile);
        return toUserProfileResponse(savedUser, savedProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse retakeOnboarding(OnboardingSubmitRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        UserInvestmentProfile latestProfile = profileRepository
                .findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId())
                .orElse(null);
        if (!Boolean.TRUE.equals(user.getOnboardingCompleted()) || latestProfile == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, COMPLETE_ONBOARDING_BEFORE_RETAKE);
        }

        ScoredAnswers scoredAnswers = validateAndScore(request);
        UserInvestmentProfile savedProfile = saveProfile(user, scoredAnswers, ProfileSource.RETAKE_QUIZ);
        user.setOnboardingCompleted(true);
        user.setUpdatedAt(LocalDateTime.now());
        AppUser savedUser = appUserRepository.save(user);
        registerAfterCommitTrigger(() -> stockAiSuggestionTriggerService.handleProfileRetaken(savedUser, savedProfile), savedUser, savedProfile);
        return toUserProfileResponse(savedUser, savedProfile);
    }

    private UserInvestmentProfile saveProfile(AppUser user, ScoredAnswers scoredAnswers, ProfileSource profileSource) {
        LocalDateTime now = LocalDateTime.now();
        UserInvestmentProfile profile = new UserInvestmentProfile();
        profile.setUser(user);
        profile.setRiskTolerance(scoredAnswers.riskTolerance());
        profile.setInvestmentGoal(scoredAnswers.investmentGoal());
        profile.setExperienceLevel(scoredAnswers.experienceLevel());
        profile.setPreferredVolatility(scoredAnswers.preferredVolatility());
        profile.setPreferredHorizon(scoredAnswers.preferredHorizon());
        profile.setRiskScore(scoredAnswers.riskScore());
        profile.setGoalScore(scoredAnswers.goalScore());
        profile.setExperienceScore(scoredAnswers.experienceScore());
        profile.setBehaviorRiskScore(null);
        profile.setBehaviorStyle(null);
        profile.setBehaviorConfidence(null);
        profile.setProfileSource(profileSource);
        profile.setProfileVersion(profileRepository.findMaxProfileVersionByUserId(user.getUserId()) + 1);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return profileRepository.save(profile);
    }

    private void registerAfterCommitTrigger(Supplier<SuggestionTriggerResult> trigger, AppUser user, UserInvestmentProfile profile) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    backgroundTaskExecutor.execute(() -> runBackgroundSuggestionTrigger(trigger, user, profile));
                } catch (RuntimeException e) {
                    log.warn("US004 after-commit AI suggestion trigger could not be scheduled userId={} profileId={} profileVersion={}",
                            user.getUserId(), profile.getProfileId(), profile.getProfileVersion(), e);
                }
            }
        });
    }

    private void runBackgroundSuggestionTrigger(
            Supplier<SuggestionTriggerResult> trigger,
            AppUser user,
            UserInvestmentProfile profile
    ) {
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                SuggestionTriggerResult result = trigger.get();
                logTriggerResult(user, profile, result);
            });
        } catch (RuntimeException e) {
            log.warn("US004 background AI suggestion trigger failed userId={} profileId={} profileVersion={}",
                    user.getUserId(),
                    profile.getProfileId(),
                    profile.getProfileVersion(),
                    e);
        }
    }

    private void logTriggerResult(AppUser user, UserInvestmentProfile profile, SuggestionTriggerResult result) {
        if (result == null) {
            log.warn("US004 after-commit AI suggestion trigger returned no result userId={} profileId={} profileVersion={}",
                    user.getUserId(), profile.getProfileId(), profile.getProfileVersion());
            return;
        }
        if (result.failed()) {
            log.warn("US004 after-commit AI suggestion trigger failed userId={} profileId={} profileVersion={} triggerReason={} message={}",
                    user.getUserId(), profile.getProfileId(), profile.getProfileVersion(), result.triggerReason(), result.message());
            return;
        }
        if (result.batchId() == null) {
            log.info("US004 after-commit AI suggestion trigger completed without batch userId={} profileId={} profileVersion={} triggerReason={} message={}",
                    user.getUserId(), profile.getProfileId(), profile.getProfileVersion(), result.triggerReason(), result.message());
            return;
        }
        log.info("US004 after-commit AI suggestion trigger completed userId={} profileId={} profileVersion={} triggerReason={} batchId={} batchStatus={} message={}",
                user.getUserId(),
                profile.getProfileId(),
                profile.getProfileVersion(),
                result.triggerReason(),
                result.batchId(),
                result.batchStatus(),
                result.message());
    }

    private ScoredAnswers validateAndScore(OnboardingSubmitRequest request) {
        if (request == null || request.answers() == null) {
            throw new IllegalArgumentException("Onboarding answers are required");
        }
        if (request.answers().isEmpty()) {
            throw new IllegalArgumentException("Onboarding answers must not be empty");
        }

        Map<String, String> selectedOptionByQuestion = new LinkedHashMap<>();
        for (OnboardingAnswerRequest answer : request.answers()) {
            String questionId = normalize(answer == null ? null : answer.questionId());
            String optionId = normalize(answer == null ? null : answer.optionId());
            if (questionId.isBlank()) {
                throw new IllegalArgumentException("Question id is required");
            }
            if (optionId.isBlank()) {
                throw new IllegalArgumentException("Option id is required for question: " + questionId);
            }
            QuestionDefinition question = QUESTION_BY_ID.get(questionId);
            if (question == null) {
                throw new IllegalArgumentException("Unknown onboarding question: " + questionId);
            }
            if (selectedOptionByQuestion.containsKey(questionId)) {
                throw new IllegalArgumentException("Duplicate onboarding answer for question: " + questionId);
            }
            if (!question.hasOption(optionId)) {
                boolean optionExistsForAnotherQuestion = QUESTIONS.stream().anyMatch(candidate -> candidate.hasOption(optionId));
                if (optionExistsForAnotherQuestion) {
                    throw new IllegalArgumentException("Option " + optionId + " does not belong to question: " + questionId);
                }
                throw new IllegalArgumentException("Unknown onboarding option: " + optionId);
            }
            selectedOptionByQuestion.put(questionId, optionId);
        }

        for (QuestionDefinition question : QUESTIONS) {
            if (!selectedOptionByQuestion.containsKey(question.questionId())) {
                throw new IllegalArgumentException("Missing onboarding answer for question: " + question.questionId());
            }
        }

        int riskScore = clampScore(Math.round((
                riskScore("risk_reaction", selectedOptionByQuestion)
                        + riskScore("volatility_comfort", selectedOptionByQuestion)
                        + riskScore("concentration_comfort", selectedOptionByQuestion)
                        + riskScore("loss_tolerance", selectedOptionByQuestion)
                        + riskScore("guidance_preference", selectedOptionByQuestion)
        ) / 5.0f));
        InvestmentGoal investmentGoal = QUESTION_BY_ID.get("investment_goal")
                .option(selectedOptionByQuestion.get("investment_goal"))
                .investmentGoal();
        ExperienceLevel experienceLevel = QUESTION_BY_ID.get("experience_level")
                .option(selectedOptionByQuestion.get("experience_level"))
                .experienceLevel();
        PreferredHorizon preferredHorizon = QUESTION_BY_ID.get("investment_horizon")
                .option(selectedOptionByQuestion.get("investment_horizon"))
                .preferredHorizon();
        PreferredVolatility selectedVolatility = QUESTION_BY_ID.get("volatility_comfort")
                .option(selectedOptionByQuestion.get("volatility_comfort"))
                .preferredVolatility();

        return new ScoredAnswers(
                riskTolerance(riskScore),
                investmentGoal,
                experienceLevel,
                preferredVolatility(selectedVolatility, riskScore),
                preferredHorizon,
                riskScore,
                goalScore(investmentGoal),
                experienceScore(experienceLevel)
        );
    }

    private int riskScore(String questionId, Map<String, String> selectedOptionByQuestion) {
        return Objects.requireNonNull(QUESTION_BY_ID.get(questionId).option(selectedOptionByQuestion.get(questionId)).riskScore());
    }

    private UserProfileResponse toUserProfileResponse(AppUser user, UserInvestmentProfile profile) {
        BehaviorSummaryForSuggestion behaviorSummary = behaviorProfileService.getBehaviorSummaryForSuggestion(user.getUserId());
        return new UserProfileResponse(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getOnboardingCompleted(),
                toInvestmentProfileResponse(profile),
                toBehaviorProfileSummaryResponse(behaviorSummary)
        );
    }

    private InvestmentProfileResponse toInvestmentProfileResponse(UserInvestmentProfile profile) {
        if (profile == null) {
            return null;
        }
        return new InvestmentProfileResponse(
                profile.getProfileId(),
                profile.getProfileVersion(),
                profile.getProfileSource() == null ? null : profile.getProfileSource().name(),
                profile.getRiskTolerance() == null ? null : profile.getRiskTolerance().name(),
                profile.getInvestmentGoal() == null ? null : profile.getInvestmentGoal().name(),
                profile.getExperienceLevel() == null ? null : profile.getExperienceLevel().name(),
                profile.getPreferredVolatility() == null ? null : profile.getPreferredVolatility().name(),
                profile.getPreferredHorizon() == null ? null : profile.getPreferredHorizon().name(),
                profile.getRiskScore(),
                profile.getGoalScore(),
                profile.getExperienceScore(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private BehaviorProfileSummaryResponse toBehaviorProfileSummaryResponse(BehaviorSummaryForSuggestion summary) {
        return new BehaviorProfileSummaryResponse(
                summary.behaviorProfileId(),
                summary.behaviorConfidence() == null ? null : summary.behaviorConfidence().name(),
                summary.behaviorStyle() == null ? null : summary.behaviorStyle().name(),
                summary.behaviorRiskScore(),
                summary.behaviorSummaryText(),
                summary.sourceNote(),
                summary.updatedAt()
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static RiskTolerance riskTolerance(int riskScore) {
        if (riskScore <= 39) {
            return RiskTolerance.CONSERVATIVE;
        }
        if (riskScore <= 69) {
            return RiskTolerance.MODERATE;
        }
        return RiskTolerance.AGGRESSIVE;
    }

    private static PreferredVolatility preferredVolatility(PreferredVolatility selectedVolatility, int riskScore) {
        if (selectedVolatility == PreferredVolatility.LOW && riskScore <= 55) {
            return PreferredVolatility.LOW;
        }
        if (selectedVolatility == PreferredVolatility.HIGH && riskScore >= 55) {
            return PreferredVolatility.HIGH;
        }
        return PreferredVolatility.MEDIUM;
    }

    private static int goalScore(InvestmentGoal investmentGoal) {
        return switch (investmentGoal) {
            case STABLE -> 25;
            case LEARNING -> 40;
            case BALANCED -> 55;
            case GROWTH -> 75;
        };
    }

    private static int experienceScore(ExperienceLevel experienceLevel) {
        return switch (experienceLevel) {
            case BEGINNER -> 20;
            case BASIC -> 45;
            case INTERMEDIATE -> 70;
        };
    }

    private static QuestionDefinition riskQuestion(String questionId, String text, List<OptionDefinition> options) {
        return new QuestionDefinition(questionId, text, options);
    }

    private static QuestionDefinition volatilityQuestion() {
        return new QuestionDefinition(
                "volatility_comfort",
                "What type of price movement are you most comfortable with?",
                List.of(
                        volatilityOption("volatility_low", "Small and stable movements.", 25, PreferredVolatility.LOW),
                        volatilityOption("volatility_medium", "Some ups and downs are acceptable.", 55, PreferredVolatility.MEDIUM),
                        volatilityOption("volatility_high", "Large movements are acceptable if growth potential is higher.", 85, PreferredVolatility.HIGH)
                )
        );
    }

    private static QuestionDefinition goalQuestion() {
        return new QuestionDefinition(
                "investment_goal",
                "What is your main goal when using StockMentor?",
                List.of(
                        goalOption("goal_learning", "I mainly want to learn how stock investing works.", InvestmentGoal.LEARNING),
                        goalOption("goal_stable", "I prefer stable and lower-risk stocks.", InvestmentGoal.STABLE),
                        goalOption("goal_growth", "I want to focus on growth opportunities.", InvestmentGoal.GROWTH),
                        goalOption("goal_balanced", "I want a balance between stability and growth.", InvestmentGoal.BALANCED)
                )
        );
    }

    private static QuestionDefinition experienceQuestion() {
        return new QuestionDefinition(
                "experience_level",
                "How would you describe your investing experience?",
                List.of(
                        experienceOption("experience_beginner", "I am new and have little or no experience.", ExperienceLevel.BEGINNER),
                        experienceOption("experience_basic", "I understand basic stock concepts but still need guidance.", ExperienceLevel.BASIC),
                        experienceOption("experience_intermediate", "I have some experience comparing stocks and reading charts.", ExperienceLevel.INTERMEDIATE)
                )
        );
    }

    private static QuestionDefinition horizonQuestion() {
        return new QuestionDefinition(
                "investment_horizon",
                "How long would you usually prefer to hold a stock in a simulated portfolio?",
                List.of(
                        horizonOption("horizon_short", "A short period; I prefer quicker decisions.", PreferredHorizon.SHORT_TERM),
                        horizonOption("horizon_medium", "A few weeks or months.", PreferredHorizon.MEDIUM_TERM),
                        horizonOption("horizon_long", "Longer term; I can wait for results.", PreferredHorizon.LONG_TERM)
                )
        );
    }

    private static OptionDefinition riskOption(String optionId, String text, int riskScore) {
        return new OptionDefinition(optionId, text, riskScore, null, null, null, null);
    }

    private static OptionDefinition volatilityOption(String optionId, String text, int riskScore, PreferredVolatility preferredVolatility) {
        return new OptionDefinition(optionId, text, riskScore, null, null, preferredVolatility, null);
    }

    private static OptionDefinition goalOption(String optionId, String text, InvestmentGoal investmentGoal) {
        return new OptionDefinition(optionId, text, null, investmentGoal, null, null, null);
    }

    private static OptionDefinition experienceOption(String optionId, String text, ExperienceLevel experienceLevel) {
        return new OptionDefinition(optionId, text, null, null, experienceLevel, null, null);
    }

    private static OptionDefinition horizonOption(String optionId, String text, PreferredHorizon preferredHorizon) {
        return new OptionDefinition(optionId, text, null, null, null, null, preferredHorizon);
    }

    private record QuestionDefinition(
            String questionId,
            String text,
            List<OptionDefinition> options
    ) {
        private boolean hasOption(String optionId) {
            return options.stream().anyMatch(option -> option.optionId().equals(optionId));
        }

        private OptionDefinition option(String optionId) {
            return options.stream()
                    .filter(option -> option.optionId().equals(optionId))
                    .findFirst()
                    .orElseThrow();
        }

        private OnboardingQuestionDto toResponse() {
            return new OnboardingQuestionDto(
                    questionId,
                    text,
                    true,
                    options.stream().map(OptionDefinition::toResponse).toList()
            );
        }
    }

    private record OptionDefinition(
            String optionId,
            String text,
            Integer riskScore,
            InvestmentGoal investmentGoal,
            ExperienceLevel experienceLevel,
            PreferredVolatility preferredVolatility,
            PreferredHorizon preferredHorizon
    ) {
        private OnboardingOptionDto toResponse() {
            return new OnboardingOptionDto(optionId, text, text);
        }
    }

    private record ScoredAnswers(
            RiskTolerance riskTolerance,
            InvestmentGoal investmentGoal,
            ExperienceLevel experienceLevel,
            PreferredVolatility preferredVolatility,
            PreferredHorizon preferredHorizon,
            Integer riskScore,
            Integer goalScore,
            Integer experienceScore
    ) {
    }
}
