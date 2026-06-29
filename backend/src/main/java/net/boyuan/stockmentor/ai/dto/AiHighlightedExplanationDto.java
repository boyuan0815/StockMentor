package net.boyuan.stockmentor.ai.dto;

import java.util.List;

public record AiHighlightedExplanationDto(
        String explanation,
        List<AiHighlightPhraseDto> highlights
) {
}
