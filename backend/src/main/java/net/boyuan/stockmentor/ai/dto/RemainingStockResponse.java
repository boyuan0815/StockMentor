package net.boyuan.stockmentor.ai.dto;

import net.boyuan.stockmentor.market.stock.dto.DelayedPriceMetadataResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        Boolean isWatchlisted,
        BigDecimal displayedPrice,
        BigDecimal displayedAbsoluteChange,
        BigDecimal displayedPercentChange,
        BigDecimal previousClose,
        String priceFreshnessStatus,
        String priceFreshnessLabel,
        DelayedPriceMetadataResponse delayedPriceMetadata,
        String displayDataSource,
        LocalDateTime displayedMarketTime
) {
}
