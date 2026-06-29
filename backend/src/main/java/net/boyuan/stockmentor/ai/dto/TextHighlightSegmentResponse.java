package net.boyuan.stockmentor.ai.dto;

public record TextHighlightSegmentResponse(
        int startIndex,
        int endIndex,
        String style
) {
}
