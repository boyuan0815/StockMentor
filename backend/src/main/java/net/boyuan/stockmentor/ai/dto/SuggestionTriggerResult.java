package net.boyuan.stockmentor.ai.dto;

import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;

public record SuggestionTriggerResult(
        boolean attempted,
        boolean successful,
        boolean failed,
        Long batchId,
        String batchStatus,
        StockAiSuggestionTriggerReason triggerReason,
        String message
) {
    public static SuggestionTriggerResult skipped(StockAiSuggestionTriggerReason triggerReason, String message) {
        return new SuggestionTriggerResult(false, false, false, null, null, triggerReason, message);
    }

    public static SuggestionTriggerResult success(
            StockAiSuggestionTriggerReason triggerReason,
            Long batchId,
            String batchStatus,
            String message
    ) {
        return new SuggestionTriggerResult(true, true, false, batchId, batchStatus, triggerReason, message);
    }

    public static SuggestionTriggerResult failure(StockAiSuggestionTriggerReason triggerReason, String message) {
        return new SuggestionTriggerResult(true, false, true, null, null, triggerReason, message);
    }
}
