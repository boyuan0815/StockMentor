package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.boyuan.stockmentor.ai.dto.AiHighlightPhraseDto;
import net.boyuan.stockmentor.ai.dto.TextHighlightSegmentResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AiHighlightSupport {
    private static final int MAX_SEGMENTS = 3;
    private static final int MAX_PHRASE_LENGTH = 80;
    private static final Set<String> ALLOWED_STYLES = Set.of("positive", "negative", "emphasis");
    private static final TypeReference<List<TextHighlightSegmentResponse>> SEGMENT_LIST_TYPE = new TypeReference<>() {
    };

    private AiHighlightSupport() {
    }

    static List<TextHighlightSegmentResponse> validatedSegments(String text, List<AiHighlightPhraseDto> highlights) {
        if (text == null || text.isBlank() || highlights == null || highlights.isEmpty()) {
            return List.of();
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        List<TextHighlightSegmentResponse> segments = new ArrayList<>();
        for (AiHighlightPhraseDto highlight : highlights) {
            if (segments.size() >= MAX_SEGMENTS || highlight == null) {
                break;
            }

            String phrase = normalizePhrase(highlight.phrase());
            String style = normalizeStyle(highlight.style());
            if (phrase == null || style == null) {
                continue;
            }

            int start = lowerText.indexOf(phrase.toLowerCase(Locale.ROOT));
            if (start < 0) {
                continue;
            }
            int end = start + phrase.length();
            if (segments.stream().anyMatch(segment -> rangesOverlap(start, end, segment))) {
                continue;
            }
            segments.add(new TextHighlightSegmentResponse(start, end, style));
        }

        return segments.stream()
                .sorted(Comparator.comparingInt(TextHighlightSegmentResponse::startIndex))
                .toList();
    }

    static String serialize(ObjectMapper objectMapper, List<TextHighlightSegmentResponse> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(segments);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    static List<TextHighlightSegmentResponse> deserialize(ObjectMapper objectMapper, String raw, String text) {
        if (raw == null || raw.isBlank() || text == null) {
            return List.of();
        }
        try {
            List<TextHighlightSegmentResponse> parsed = objectMapper.readValue(raw, SEGMENT_LIST_TYPE);
            if (parsed == null) {
                return List.of();
            }
            return parsed.stream()
                    .filter(segment -> isValidStoredSegment(segment, text.length()))
                    .sorted(Comparator.comparingInt(TextHighlightSegmentResponse::startIndex))
                    .limit(MAX_SEGMENTS)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static String stripRawHighlightMarkup(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)</?[a-z][a-z0-9-]*(?:\\s+[^<>]*)?>", "")
                .replaceAll("(?i)\\[(?:/?)(green|red|bold|positive|negative|emphasis|mark|strong|span|highlight)]", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("__([^_]+)__", "$1");
    }

    private static String normalizePhrase(String phrase) {
        if (phrase == null) {
            return null;
        }
        String normalized = phrase.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank() || normalized.length() > MAX_PHRASE_LENGTH) {
            return null;
        }
        return normalized;
    }

    private static String normalizeStyle(String style) {
        if (style == null) {
            return null;
        }
        String normalized = style.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_STYLES.contains(normalized) ? normalized : null;
    }

    private static boolean rangesOverlap(int start, int end, TextHighlightSegmentResponse segment) {
        return start < segment.endIndex() && end > segment.startIndex();
    }

    private static boolean isValidStoredSegment(TextHighlightSegmentResponse segment, int textLength) {
        return segment != null
                && segment.startIndex() >= 0
                && segment.endIndex() > segment.startIndex()
                && segment.endIndex() <= textLength
                && ALLOWED_STYLES.contains(segment.style());
    }
}
