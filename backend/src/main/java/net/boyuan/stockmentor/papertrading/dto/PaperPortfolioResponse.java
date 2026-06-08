package net.boyuan.stockmentor.papertrading.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaperPortfolioResponse(
        BigDecimal cashBalance,
        BigDecimal startingCash,
        BigDecimal totalInvestedCost,
        BigDecimal estimatedMarketValue,
        BigDecimal estimatedPortfolioValue,
        BigDecimal unrealizedProfitLoss,
        List<PaperPositionResponse> positions
) {
}
