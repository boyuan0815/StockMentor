package net.boyuan.stockmentor.ai.dto.admin;

import java.util.List;

public record AdminAiSuggestionUsageSummaryResponse(
        Long totalBatches,
        Long successCount,
        Long failedCount,
        Long fallbackCachedCount,
        Long fallbackRuleBasedCount,
        Long totalPromptTokens,
        Long totalCompletionTokens,
        Long totalTokens,
        List<AdminAiSuggestionGroupedCountResponse> groupedByTriggerReason,
        List<AdminAiSuggestionGroupedCountResponse> groupedByStatus
) {
}
