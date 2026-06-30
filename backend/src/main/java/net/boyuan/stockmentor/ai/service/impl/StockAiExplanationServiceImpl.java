package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.AiHighlightedExplanationDto;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import net.boyuan.stockmentor.ai.dto.TextHighlightSegmentResponse;
import net.boyuan.stockmentor.ai.entity.StockAiExplanation;
import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.service.OpenAiClient;
import net.boyuan.stockmentor.ai.service.StockAiExplanationService;
import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockAiExplanationServiceImpl implements StockAiExplanationService {
    private final StockAnalysisService stockAnalysisService;
    private final StockAiExplanationRepository explanationRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final DelayedMarketPriceService delayedMarketPriceService;

    private static final String PROMPT_VERSION = "stock-explanation-v3";
    private static final String UNAVAILABLE_MESSAGE = "AI explanation is currently unavailable. Please try again later.";
    private static final String SYSTEM_PROMPT = """
            Follow all instructions strictly. If any rule is violated, regenerate the answer.

            You are a StockMentor learning guide writing short explanations for beginner paper-trading users.

            You will be given structured stock data.

            Your job:
            - Explain trend, volatility, volume, price range, and price consistency in simple language
            - Include one simple "what to watch next" idea, such as volume, trend direction, recent high, or recent low
            - Do NOT invent any external news or reasons
            - Do NOT predict future prices
            - Do NOT give investment advice (e.g., do not say buy, sell, or opportunity)
            - Do not quote exact price or percent change unless the user input explicitly marks them as visible quote fields

            Writing style:
            - Simple English for beginners
            - Clear, concise, and mobile-readable
            - Use shorter sentences
            - Avoid technical jargon unless explained simply
            - Avoid overconfident or absolute statements
            - Prefer "may suggest", "could indicate", or "may reflect"
            - Prefer specific explanations over generic phrases
            - Avoid abstract phrases such as "cautious participation", "investor sentiment", or "controlled buying"
            - If using "accumulation", explain it as gradual buying
            - Do not say low-volatility data is highly erratic
            - Do not describe volatile movement as fully controlled
            - Avoid generic statements such as "prices may fluctuate" without context
            - One or two short paragraphs are fine

            Highlight rules:
            - Return up to 3 exact phrases from the final explanation that would help a beginner scan the reason.
            - Use style "positive" for supportive data, "negative" for caution/risk data, and "emphasis" for neutral important concepts.
            - Highlight only short useful phrases, not long sentences.
            - Do not over-highlight neutral wording.
            - Do not add visible tags or markdown in the explanation text.
            - If no phrase is worth highlighting, return an empty highlights array.

            Output format:
            - Return raw JSON only.
            - Do not wrap the JSON in markdown.
            - Use exactly this shape:
            {
              "explanation": "Two short paragraphs.",
              "highlights": [
                {"phrase": "exact phrase from explanation", "style": "positive"}
              ]
            }
            """;

    @Override
    public StockExplanationResponse getOrGenerateExplanation(String symbol, String timeframe) {
        StockAnalysisSnapshot snapshot = stockAnalysisService.createOrReuseSnapshot(symbol, timeframe);
        String model = openAiClient.getModel();

        return explanationRepository.findByAnalysisSnapshotAndModelAndPromptVersion(snapshot, model, PROMPT_VERSION)
                .map(explanation -> toResponse(snapshot, explanation, true))
                .orElseGet(() -> generateAndStore(snapshot, model));
    }

    private StockExplanationResponse generateAndStore(StockAnalysisSnapshot snapshot, String model) {
        String userContent = buildExplanationUserContent(snapshot);
        OpenAiExplanationResult result = openAiClient.generateExplanation(SYSTEM_PROMPT, userContent);

        if (!result.success()) {
            return new StockExplanationResponse(
                    snapshot.getSymbol(),
                    snapshot.getTimeframe(),
                    UNAVAILABLE_MESSAGE,
                    List.of(),
                    false,
                    false,
                    snapshot.getAnalysisSnapshotId(),
                    snapshot.getDataStartDate(),
                    snapshot.getDataEndDate(),
                    snapshot.getDataSource(),
                    snapshot.getIsFallback(),
                    snapshot.getBaselineRiskCategory(),
                    snapshot.getRiskCategory(),
                    result.errorMessage()
            );
        }

        ParsedExplanation parsed;
        try {
            parsed = parseExplanation(result.content());
        } catch (InvalidAiExplanationResponseException e) {
            return new StockExplanationResponse(
                    snapshot.getSymbol(),
                    snapshot.getTimeframe(),
                    UNAVAILABLE_MESSAGE,
                    List.of(),
                    false,
                    false,
                    snapshot.getAnalysisSnapshotId(),
                    snapshot.getDataStartDate(),
                    snapshot.getDataEndDate(),
                    snapshot.getDataSource(),
                    snapshot.getIsFallback(),
                    snapshot.getBaselineRiskCategory(),
                    snapshot.getRiskCategory(),
                    e.getMessage()
            );
        }

        StockAiExplanation explanation = new StockAiExplanation();
        explanation.setAnalysisSnapshot(snapshot);
        explanation.setSymbol(snapshot.getSymbol());
        explanation.setTimeframe(snapshot.getTimeframe());
        explanation.setModel(model);
        explanation.setPromptVersion(PROMPT_VERSION);
        explanation.setExplanation(limitText(parsed.explanation(), 2000));
        explanation.setExplanationHighlights(AiHighlightSupport.serialize(objectMapper, parsed.highlights()));
        explanation.setPromptTokens(result.promptTokens());
        explanation.setCompletionTokens(result.completionTokens());
        explanation.setTotalTokens(result.totalTokens());
        explanation.setFinishReason(result.finishReason());
        explanation.setCreatedAt(LocalDateTime.now());

        StockAiExplanation saved = explanationRepository.save(explanation);
        return toResponse(snapshot, saved, false);
    }

    private StockExplanationResponse toResponse(StockAnalysisSnapshot snapshot, StockAiExplanation explanation, boolean cached) {
        return new StockExplanationResponse(
                snapshot.getSymbol(),
                snapshot.getTimeframe(),
                explanation.getExplanation(),
                AiHighlightSupport.deserialize(objectMapper, explanation.getExplanationHighlights(), explanation.getExplanation()),
                cached,
                true,
                snapshot.getAnalysisSnapshotId(),
                snapshot.getDataStartDate(),
                snapshot.getDataEndDate(),
                snapshot.getDataSource(),
                snapshot.getIsFallback(),
                snapshot.getBaselineRiskCategory(),
                snapshot.getRiskCategory(),
                cached ? "Returned cached AI explanation" : "Generated new AI explanation"
        );
    }

    private String buildExplanationUserContent(StockAnalysisSnapshot snapshot) {
        if (!"1D".equalsIgnoreCase(snapshot.getTimeframe())) {
            return stockAnalysisService.buildPromptUserContent(snapshot);
        }

        DelayedMarketPrice visibleQuote = resolveVisibleQuote(snapshot.getSymbol());
        String dataNote = Boolean.TRUE.equals(snapshot.getIsFallback())
                ? "\nData note: This analysis uses daily candle fallback because complete 1-minute intraday data was unavailable."
                : "";
        String exactQuoteInstruction = visibleQuote == null
                ? "Do not mention exact price or percent change because the visible quote was unavailable."
                : "If mentioning exact price or percent change, use only the visible quote fields because they match the stock detail header.";

        return """
                Symbol: %s
                Time frame: %s
                Visible quote price: %s
                Visible quote percent change vs previous close: %s%%
                Visible quote freshness: %s
                Analysis trend: %s
                Analysis volatility: %s
                Analysis volume trend: %s
                Analysis period high: %s
                Analysis period low: %s
                Risk category: %s
                Price consistency: %s
                Instruction: %s%s
                """.formatted(
                snapshot.getSymbol(),
                snapshot.getTimeframe(),
                format(visibleQuote == null ? null : visibleQuote.displayedPrice()),
                formatSigned(visibleQuote == null ? null : visibleQuote.displayedPercentChange()),
                visibleQuote == null ? "unavailable" : valueOrUnavailable(visibleQuote.priceFreshnessLabel()),
                valueOrUnavailable(snapshot.getTrend()),
                valueOrUnavailable(snapshot.getVolatilityLabel()),
                valueOrUnavailable(snapshot.getVolumeTrend()),
                format(snapshot.getHighPrice()),
                format(snapshot.getLowPrice()),
                valueOrUnavailable(snapshot.getRiskCategory()),
                valueOrUnavailable(snapshot.getPriceConsistency()),
                exactQuoteInstruction,
                dataNote
        );
    }

    private DelayedMarketPrice resolveVisibleQuote(String symbol) {
        try {
            return delayedMarketPriceService.resolveForDisplay(symbol);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ParsedExplanation parseExplanation(String rawContent) {
        String cleanedJson = cleanJson(rawContent);
        try {
            AiHighlightedExplanationDto parsed = objectMapper.readValue(cleanedJson, AiHighlightedExplanationDto.class);
            String explanation = cleanExplanationText(parsed.explanation());
            if (explanation == null || explanation.isBlank()) {
                throw new IllegalArgumentException("AI returned blank explanation");
            }
            return new ParsedExplanation(
                    explanation,
                    AiHighlightSupport.validatedSegments(explanation, parsed.highlights())
            );
        } catch (Exception ignored) {
            if (isJsonShaped(cleanedJson)) {
                throw new InvalidAiExplanationResponseException("AI explanation format was invalid");
            }
            String explanation = cleanExplanationText(rawContent);
            if (explanation == null || explanation.isBlank()) {
                throw new InvalidAiExplanationResponseException("AI explanation text was blank");
            }
            return new ParsedExplanation(explanation, List.of());
        }
    }

    private String cleanJson(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return value;
    }

    private boolean isJsonShaped(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String cleanExplanationText(String value) {
        if (value == null) {
            return null;
        }
        return AiHighlightSupport.stripRawHighlightMarkup(value)
                .replace("\r\n", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String format(BigDecimal value) {
        return value == null ? "unavailable" : value.stripTrailingZeros().toPlainString();
    }

    private String formatSigned(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }
        String formatted = value.stripTrailingZeros().toPlainString();
        return value.signum() > 0 ? "+" + formatted : formatted;
    }

    private String valueOrUnavailable(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private record ParsedExplanation(String explanation, List<TextHighlightSegmentResponse> highlights) {
    }

    private static class InvalidAiExplanationResponseException extends RuntimeException {
        InvalidAiExplanationResponseException(String message) {
            super(message);
        }
    }
}
