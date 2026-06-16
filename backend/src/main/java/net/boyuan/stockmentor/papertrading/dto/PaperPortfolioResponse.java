package net.boyuan.stockmentor.papertrading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaperPortfolioResponse(
        Long userId,
        BigDecimal cashBalance,
        BigDecimal startingCash,
        BigDecimal totalInvestedCost,
        BigDecimal estimatedMarketValue,
        BigDecimal totalPortfolioValue,
        BigDecimal unrealizedProfitLoss,
        BigDecimal realizedProfitLoss,
        BigDecimal returnPercentage,
        BigDecimal totalFeesPaid,
        Integer currentSessionNumber,
        LocalDateTime lastResetAt,
        List<PaperPositionResponse> positions,
        Integer pricedPositionCount,
        Integer unpricedPositionCount,
        Boolean portfolioValuationComplete,
        String portfolioDataNote
) {
}
