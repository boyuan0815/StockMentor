package net.boyuan.stockmentor.ai.controller;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks/ai-suggestions")
@RequiredArgsConstructor
public class StockAiSuggestionController {
    private final StockAiSuggestionService stockAiSuggestionService;

    @GetMapping
    public StockAiSuggestionResponse getSuggestions() {
        return stockAiSuggestionService.getSuggestionsForCurrentUser();
    }

    @PostMapping("/refresh")
    public StockAiSuggestionResponse refreshSuggestions() {
        return stockAiSuggestionService.refreshSuggestionsForCurrentUser();
    }

    @PatchMapping("/items/{itemId}/dismiss")
    public StockAiSuggestionResponse dismissSuggestion(@PathVariable Long itemId) {
        return stockAiSuggestionService.dismissSuggestionForCurrentUser(itemId);
    }

    @PatchMapping("/items/{itemId}/watchlist")
    public StockAiSuggestionResponse watchlistSuggestion(@PathVariable Long itemId) {
        return stockAiSuggestionService.watchlistSuggestionForCurrentUser(itemId);
    }
}
