package net.boyuan.stockmentor.ai.dto;

public record AiSuggestedStockDto(
        String symbol,
        Integer rankNo,
        Integer matchScore,
        String riskLevel,
        String suggestionLabel,
        String shortReason,
        String detailReason
) {
}
