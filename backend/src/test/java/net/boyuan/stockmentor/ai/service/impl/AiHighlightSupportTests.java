package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.boyuan.stockmentor.ai.dto.AiHighlightPhraseDto;
import net.boyuan.stockmentor.ai.dto.TextHighlightSegmentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHighlightSupportTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatedSegmentsAcceptsOnlyKnownStylesAndExactPhrases() {
        List<TextHighlightSegmentResponse> segments = AiHighlightSupport.validatedSegments(
                "Steady demand improved while choppy movement added risk.",
                List.of(
                        new AiHighlightPhraseDto("Steady demand", "positive"),
                        new AiHighlightPhraseDto("choppy movement", "negative"),
                        new AiHighlightPhraseDto("missing phrase", "emphasis"),
                        new AiHighlightPhraseDto("risk", "loud")
                )
        );

        assertEquals(2, segments.size());
        assertEquals("positive", segments.get(0).style());
        assertEquals("negative", segments.get(1).style());
    }

    @Test
    void deserializeDropsMalformedStoredSegments() {
        String json = AiHighlightSupport.serialize(
                objectMapper,
                List.of(new TextHighlightSegmentResponse(0, 6, "emphasis"))
        );

        assertEquals(1, AiHighlightSupport.deserialize(objectMapper, json, "Strong text").size());
        assertTrue(AiHighlightSupport.deserialize(objectMapper, "[{\"startIndex\":8,\"endIndex\":99,\"style\":\"emphasis\"}]", "short").isEmpty());
        assertTrue(AiHighlightSupport.deserialize(objectMapper, "not-json", "text").isEmpty());
    }

    @Test
    void rawMarkupIsRemovedBeforeDisplay() {
        assertEquals(
                "Strong demand and weak volume with steady buying",
                AiHighlightSupport.stripRawHighlightMarkup(
                        "[green]<strong>Strong demand</strong>[/green] and <mark>weak volume</mark> with **steady buying**")
        );
    }
}
