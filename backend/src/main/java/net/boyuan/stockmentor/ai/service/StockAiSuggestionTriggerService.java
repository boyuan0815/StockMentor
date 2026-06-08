package net.boyuan.stockmentor.ai.service;

import net.boyuan.stockmentor.ai.dto.SuggestionTriggerResult;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;

public interface StockAiSuggestionTriggerService {
    SuggestionTriggerResult handleOnboardingCompleted(AppUser user);

    SuggestionTriggerResult handleProfileRetaken(AppUser user, UserInvestmentProfile profile);

    SuggestionTriggerResult handleInvestmentProfileChanged(
            AppUser user,
            UserInvestmentProfile profile,
            StockAiSuggestionTriggerReason reason
    );
}
