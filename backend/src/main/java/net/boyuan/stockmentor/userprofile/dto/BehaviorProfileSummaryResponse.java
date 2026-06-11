package net.boyuan.stockmentor.userprofile.dto;

import java.time.LocalDateTime;

public record BehaviorProfileSummaryResponse(
        Long behaviorProfileId,
        String behaviorConfidence,
        String behaviorStyle,
        Integer behaviorRiskScore,
        String behaviorSummaryText,
        String sourceNote,
        LocalDateTime updatedAt
) {
}
