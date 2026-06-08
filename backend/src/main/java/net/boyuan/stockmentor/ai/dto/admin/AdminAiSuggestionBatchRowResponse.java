package net.boyuan.stockmentor.ai.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminAiSuggestionBatchRowResponse(
        Long batchId,
        Long userId,
        String userEmail,
        String status,
        String triggerReason,
        String analysisTimeframe,
        String model,
        String promptVersion,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        Boolean fallbackUsed,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<String> suggestedSymbols,
        Integer itemCount
) {
}
