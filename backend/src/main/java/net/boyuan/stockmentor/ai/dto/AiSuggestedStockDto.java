package net.boyuan.stockmentor.ai.dto;

import java.util.List;

public record AiSuggestedStockDto(
        String symbol,
        Integer rankNo,
        Integer matchScore,
        String riskLevel,
        String suggestionLabel,
        String shortReason,
        String detailReason,
        List<AiHighlightPhraseDto> shortReasonHighlights,
        List<AiHighlightPhraseDto> detailReasonHighlights
) {
}
