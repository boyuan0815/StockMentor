package net.boyuan.stockmentor.watchlist.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WatchlistStockResponse(
        Long stockId,
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal percentChange,
        String trend,
        String volatilityLabel,
        String riskCategory,
        Boolean isWatchlisted,
        BigDecimal previousClose,
        BigDecimal displayedAbsoluteChange,
        BigDecimal displayedPrice,
        BigDecimal displayedPercentChange,
        LocalDateTime displayedMarketTime,
        LocalDateTime targetDisplayMarketTime,
        Integer dataDelayMinutes,
        String priceFreshnessStatus,
        String priceFreshnessLabel,
        Boolean isPriceAvailable,
        Boolean isTradeExecutable,
        String dataNote,
        String priceSource,
        String marketTimeZone,
        LocalDateTime lastBackendUpdatedAt
) {
}
