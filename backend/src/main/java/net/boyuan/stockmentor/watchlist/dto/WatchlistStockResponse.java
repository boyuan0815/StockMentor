package net.boyuan.stockmentor.watchlist.dto;

import java.math.BigDecimal;

public record WatchlistStockResponse(
        Long stockId,
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal percentChange,
        String trend,
        String volatilityLabel,
        String riskCategory,
        Boolean isWatchlisted
) {
}
