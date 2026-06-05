package net.boyuan.stockmentor.ai.dto;

import java.math.BigDecimal;

public record RemainingStockResponse(
        Long stockId,
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal percentChange,
        String trend,
        String volatilityLabel,
        String riskCategory,
        Boolean isSuggested,
        Boolean isWatchlisted
) {
}
