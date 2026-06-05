package net.boyuan.stockmentor.ai.service;

import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;

public interface StockAiSuggestionService {
    StockAiSuggestionResponse getSuggestionsForCurrentUser();

    StockAiSuggestionResponse refreshSuggestionsForCurrentUser();

    StockAiSuggestionResponse dismissSuggestionForCurrentUser(Long itemId);

    StockAiSuggestionResponse watchlistSuggestionForCurrentUser(Long itemId);
}
