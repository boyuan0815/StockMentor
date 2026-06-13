package net.boyuan.stockmentor.market.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockDetailResponse(
        Long stockId,
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal percentChange,
        LocalDateTime lastUpdated,
        Boolean isMarketOpen,
        String timezone,
        String source,
        String riskCategory,
        String baselineRiskCategory,
        String trend,
        String volatilityLabel,
        String volumeTrend,
        String priceConsistency,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        String dataSource,
        Boolean isFallback,
        Integer missingDataCount,
        Long latestAnalysisSnapshotId,
        String snapshotHash,
        Boolean isWatchlisted,
        Boolean aiExplanationAvailable,
        String aiExplanationEndpoint,
        Boolean tradeSupported
) {
}
