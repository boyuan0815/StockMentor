package net.boyuan.stockmentor.ai.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.dto.SuggestionTriggerResult;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionTriggerService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class StockAiSuggestionTriggerServiceImpl implements StockAiSuggestionTriggerService {
    private static final Logger log = LoggerFactory.getLogger(StockAiSuggestionTriggerServiceImpl.class);
    private static final EnumSet<StockAiSuggestionTriggerReason> ALLOWED_PROFILE_TRIGGER_REASONS = EnumSet.of(
            StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED,
            StockAiSuggestionTriggerReason.RETAKE_QUIZ,
            StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION
    );

    private final StockAiSuggestionService stockAiSuggestionService;

    @Override
    public SuggestionTriggerResult handleOnboardingCompleted(AppUser user) {
        return triggerSuggestions(user, null, StockAiSuggestionTriggerReason.ONBOARDING_COMPLETED, false);
    }

    @Override
    public SuggestionTriggerResult handleProfileRetaken(AppUser user, UserInvestmentProfile profile) {
        return triggerSuggestions(user, profile, StockAiSuggestionTriggerReason.RETAKE_QUIZ, true);
    }

    @Override
    public SuggestionTriggerResult handleInvestmentProfileChanged(
            AppUser user,
            UserInvestmentProfile profile,
            StockAiSuggestionTriggerReason reason
    ) {
        boolean requiresSavedProfile = reason != StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION;
        return triggerSuggestions(user, profile, reason, requiresSavedProfile);
    }

    private SuggestionTriggerResult triggerSuggestions(
            AppUser user,
            UserInvestmentProfile profile,
            StockAiSuggestionTriggerReason reason,
            boolean requiresSavedProfile
    ) {
        validateUser(user);
        validateReason(reason);
        if (requiresSavedProfile) {
            validateSavedProfile(user, profile);
        }

        Long profileId = profile == null ? null : profile.getProfileId();
        Integer profileVersion = profile == null ? null : profile.getProfileVersion();
        log.info("AI suggestion trigger started userId={} profileId={} profileVersion={} triggerReason={}",
                user.getUserId(), profileId, profileVersion, reason);

        try {
            StockAiSuggestionResponse response = stockAiSuggestionService.generateSuggestionsForUser(user, reason, false);
            String message = response.message() == null ? "Suggestion trigger completed" : response.message();
            return SuggestionTriggerResult.success(reason, response.batchId(), response.batchStatus(), message);
        } catch (RuntimeException e) {
            log.warn("AI suggestion trigger failed userId={} profileId={} profileVersion={} triggerReason={}",
                    user.getUserId(), profileId, profileVersion, reason, e);
            return SuggestionTriggerResult.failure(reason, "Suggestion trigger failed");
        }
    }

    private void validateSavedProfile(AppUser user, UserInvestmentProfile profile) {
        if (profile == null || profile.getProfileId() == null) {
            throw new IllegalArgumentException("Saved investment profile is required before triggering AI suggestions");
        }
        if (profile.getUser() == null || profile.getUser().getUserId() == null) {
            throw new IllegalArgumentException("Saved investment profile must belong to the triggering user");
        }
        if (!profile.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("Saved investment profile must belong to the triggering user");
        }
    }

    private void validateUser(AppUser user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Saved app user is required before triggering AI suggestions");
        }
    }

    private void validateReason(StockAiSuggestionTriggerReason reason) {
        if (reason == null || !ALLOWED_PROFILE_TRIGGER_REASONS.contains(reason)) {
            throw new IllegalArgumentException("Unsupported AI suggestion profile trigger reason: " + reason);
        }
    }
}
