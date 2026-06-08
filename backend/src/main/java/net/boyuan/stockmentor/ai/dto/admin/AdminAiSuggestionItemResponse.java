package net.boyuan.stockmentor.ai.dto.admin;

import java.time.LocalDateTime;

public record AdminAiSuggestionItemResponse(
        Long itemId,
        String symbol,
        Integer rankNo,
        Integer matchScore,
        String riskLevel,
        String suggestionLabel,
        String shortReason,
        String status,
        Long snapshotId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
