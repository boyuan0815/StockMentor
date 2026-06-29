package net.boyuan.stockmentor.analysis.dto;

import java.time.LocalDate;
import java.util.List;

import net.boyuan.stockmentor.ai.dto.TextHighlightSegmentResponse;

public record StockExplanationResponse(
        String symbol,
        String timeframe,
        String explanation,
        List<TextHighlightSegmentResponse> explanationHighlights,
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
