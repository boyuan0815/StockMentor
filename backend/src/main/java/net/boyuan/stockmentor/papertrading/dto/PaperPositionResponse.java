package net.boyuan.stockmentor.papertrading.dto;

import net.boyuan.stockmentor.market.stock.dto.DelayedPriceMetadataResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperPositionResponse(
        Long positionId,
        String symbol,
        String companyName,
        Integer quantity,
        BigDecimal averageCost,
        BigDecimal totalCost,
        BigDecimal investedCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedProfitLoss,
        BigDecimal unrealizedProfitLossPercent,
        BigDecimal portfolioWeightPercent,
        String riskCategory,
        LocalDateTime lastUpdated,
        BigDecimal valuationPrice,
        BigDecimal valuationMarketValue,
        String valuationDataNote,
        DelayedPriceMetadataResponse delayedPriceMetadata
) {
}
