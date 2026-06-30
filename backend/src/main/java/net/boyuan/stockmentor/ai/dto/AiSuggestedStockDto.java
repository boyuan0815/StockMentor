package net.boyuan.stockmentor.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSuggestedStockDto(
        String symbol,
        Integer rankNo,
        Integer matchScore,
        String riskLevel,
        String suggestionLabel,
        String shortReason,
        String detailReason,
        List<AiHighlightPhraseDto> detailReasonHighlights
) {
}
