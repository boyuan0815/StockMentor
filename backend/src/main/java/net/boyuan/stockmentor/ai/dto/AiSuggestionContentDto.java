package net.boyuan.stockmentor.ai.dto;

import java.util.List;

public record AiSuggestionContentDto(
        String batchSummary,
        List<AiSuggestedStockDto> suggestedStocks
) {
}
