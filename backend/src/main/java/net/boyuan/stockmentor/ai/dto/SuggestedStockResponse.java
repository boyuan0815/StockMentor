package net.boyuan.stockmentor.ai.dto;

import java.math.BigDecimal;

public record SuggestedStockResponse(
        Long itemId,
        Long stockId,
        String symbol,
        String companyName,
        Integer rankNo,
        Integer matchScore,
        String riskLevel,
        String suggestionLabel,
        String shortReason,
        String detailReason,
        String status,
        Long snapshotId,
        BigDecimal currentPrice,
        BigDecimal percentChange,
        String trend,
        String volatilityLabel,
        String volumeTrend,
        String priceConsistency,
        Boolean isFallback,
        Integer missingDataCount,
        Boolean isWatchlisted
) {
}
