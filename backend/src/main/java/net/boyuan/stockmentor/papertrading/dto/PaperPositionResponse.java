package net.boyuan.stockmentor.papertrading.dto;

import java.math.BigDecimal;

public record PaperPositionResponse(
        Long positionId,
        String symbol,
        String companyName,
        Integer quantity,
        BigDecimal averageCost,
        BigDecimal totalCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedProfitLoss,
        BigDecimal unrealizedProfitLossPercent
) {
}
