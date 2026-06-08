package net.boyuan.stockmentor.ai.dto.admin;

import java.time.LocalDateTime;

public record AiSuggestionRefreshJobResponse(
        Long jobId,
        String status,
        String triggeredBy,
        Long triggeredByUserId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer processedUsers,
        Integer skippedUsers,
        Integer successCount,
        Integer reusedCount,
        Integer fallbackCount,
        Integer failedCount,
        String message
) {
}
