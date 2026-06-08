package net.boyuan.stockmentor.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StockAiSuggestionResponse(
        Long userId,
        Long batchId,
        String batchStatus,
        String triggerReason,
        String batchSummary,
        String analysisTimeframe,
        LocalDateTime generatedAt,
        LocalDateTime expiresAt,
        Boolean fallbackUsed,
        Boolean refreshAllowed,
        LocalDateTime nextRefreshAllowedAt,
        List<SuggestedStockResponse> suggestedStocks,
        List<RemainingStockResponse> remainingStocks,
        String message
) {
}
