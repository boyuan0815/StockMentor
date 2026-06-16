package net.boyuan.stockmentor.market.stock.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DelayedMarketPrice(
        String symbol,
        BigDecimal displayedPrice,
        BigDecimal displayedPercentChange,
        LocalDateTime displayedMarketTime,
        LocalDateTime targetDisplayMarketTime,
        Integer dataDelayMinutes,
        DelayedPriceFreshnessStatus priceFreshnessStatus,
        boolean priceAvailable,
        boolean tradeExecutable,
        String dataNote,
        String priceSource,
        String marketTimeZone,
        LocalDateTime lastBackendUpdatedAt,
        LocalDate tradingDate
) {
    public String priceFreshnessStatusName() {
        return priceFreshnessStatus == null ? null : priceFreshnessStatus.name();
    }
}
