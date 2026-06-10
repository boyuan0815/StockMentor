package net.boyuan.stockmentor.papertrading.dto;

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
        LocalDateTime lastUpdated
) {
}
