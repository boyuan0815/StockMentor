package net.boyuan.stockmentor.market.stock.dto;

import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DelayedPriceMetadataResponse(
        BigDecimal displayedPrice,
        BigDecimal displayedPercentChange,
        LocalDateTime displayedMarketTime,
        LocalDateTime targetDisplayMarketTime,
        Integer dataDelayMinutes,
        String priceFreshnessStatus,
        Boolean isPriceAvailable,
        Boolean isTradeExecutable,
        String dataNote,
        String priceSource,
        String marketTimeZone,
        LocalDateTime lastBackendUpdatedAt
) {
    public static DelayedPriceMetadataResponse from(DelayedMarketPrice price) {
        if (price == null) {
            return null;
        }
        return new DelayedPriceMetadataResponse(
                price.displayedPrice(),
                price.displayedPercentChange(),
                price.displayedMarketTime(),
                price.targetDisplayMarketTime(),
                price.dataDelayMinutes(),
                price.priceFreshnessStatusName(),
                price.priceAvailable(),
                price.tradeExecutable(),
                price.dataNote(),
                price.priceSource(),
                price.marketTimeZone(),
                price.lastBackendUpdatedAt()
        );
    }
}
