package net.boyuan.stockmentor.admin.user.dto;

import java.time.LocalDateTime;

public record AdminInvestmentProfileSummaryResponse(
        Long profileId,
        Integer profileVersion,
        String profileSource,
        String riskTolerance,
        String investmentGoal,
        String experienceLevel,
        String preferredVolatility,
        String preferredHorizon,
        Integer riskScore,
        Integer goalScore,
        Integer experienceScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
