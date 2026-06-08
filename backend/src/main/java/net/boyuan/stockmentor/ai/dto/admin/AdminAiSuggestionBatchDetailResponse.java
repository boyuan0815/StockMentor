package net.boyuan.stockmentor.ai.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminAiSuggestionBatchDetailResponse(
        Long batchId,
        Long userId,
        String userEmail,
        String username,
        String status,
        String triggerReason,
        String analysisTimeframe,
        String model,
        String promptVersion,
        Integer profileVersion,
        String inputHash,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        Boolean fallbackUsed,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<String> suggestedSymbols,
        Integer itemCount,
        List<AdminAiSuggestionItemResponse> items
) {
}
