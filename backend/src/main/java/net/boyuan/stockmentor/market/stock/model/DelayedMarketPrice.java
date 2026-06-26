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
        LocalDate tradingDate,
        BigDecimal previousClose,
        BigDecimal displayedAbsoluteChange,
        String priceFreshnessLabel
) {
    public DelayedMarketPrice(
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
        this(
                symbol,
                displayedPrice,
                displayedPercentChange,
                displayedMarketTime,
                targetDisplayMarketTime,
                dataDelayMinutes,
                priceFreshnessStatus,
                priceAvailable,
                tradeExecutable,
                dataNote,
                priceSource,
                marketTimeZone,
                lastBackendUpdatedAt,
                tradingDate,
                null,
                null,
                priceFreshnessStatus == null ? null : priceFreshnessStatus.label()
        );
    }

    public String priceFreshnessStatusName() {
        return priceFreshnessStatus == null ? null : priceFreshnessStatus.name();
    }
}
