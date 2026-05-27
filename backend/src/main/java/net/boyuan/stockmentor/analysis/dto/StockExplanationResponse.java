package net.boyuan.stockmentor.analysis.dto;

import java.time.LocalDate;

public record StockExplanationResponse(
        String symbol,
        String timeframe,
        String explanation,
        boolean cached,
        boolean available,
        Long analysisSnapshotId,
        LocalDate dataStartDate,
        LocalDate dataEndDate,
        String dataSource,
        Boolean isFallback,
        String baselineRiskCategory,
        String riskCategory,
        String message
) {
}
