package net.boyuan.stockmentor.ai.service;

import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.auth.entity.AppUser;

public interface StockAiSuggestionService {
    StockAiSuggestionResponse getSuggestionsForCurrentUser();

    StockAiSuggestionResponse refreshSuggestionsForCurrentUser();

    StockAiSuggestionResponse generateSuggestionsForUser(
            AppUser user,
            StockAiSuggestionTriggerReason triggerReason,
            boolean enforceManualCooldown
    );

    StockAiSuggestionResponse dismissSuggestionForCurrentUser(Long itemId);

    StockAiSuggestionResponse watchlistSuggestionForCurrentUser(Long itemId);
}
