package net.boyuan.stockmentor.ai.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import net.boyuan.stockmentor.ai.entity.StockAiExplanation;
import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.service.OpenAiClient;
import net.boyuan.stockmentor.ai.service.StockAiExplanationService;
import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StockAiExplanationServiceImpl implements StockAiExplanationService {
    private final StockAnalysisService stockAnalysisService;
    private final StockAiExplanationRepository explanationRepository;
    private final OpenAiClient openAiClient;

    private static final String PROMPT_VERSION = "stock-explanation-v1";
    private static final String UNAVAILABLE_MESSAGE = "AI explanation is currently unavailable. Please try again later.";
    private static final String SYSTEM_PROMPT = """
            Follow all instructions strictly. If any rule is violated, regenerate the answer.

            You are a financial data analyst writing explanations for beginner investors.

            You will be given structured stock data.

            Your job:
            - Interpret relationships between trend, volatility, and volume
            - You MUST explain how trend, volatility, and volume together indicate market behavior (not separately)
            - Explicitly explain what the data suggests about investor behavior (e.g., steady demand, accumulation, uncertainty, speculation)
            - You MUST explicitly refer to ALL of these inputs in your explanation: trend, volatility, volume trend, price consistency, price range (high/low), and percentage change
            - Do NOT invent any external news or reasons
            - Do NOT predict future prices
            - Do NOT give investment advice (e.g., do not say buy, sell, or opportunity)

            Writing style:
            - Simple English for beginners
            - Clear and concise
            - Avoid technical jargon unless explained simply
            - Avoid overconfident or absolute statements
            - Prefer specific explanations over generic phrases
            - Include at least one phrase describing investor behavior (e.g., accumulation, steady demand, controlled buying, cautious participation)

            Output rules:
            - 80-100 words total
            - EXACTLY 2 short paragraphs
            - Each paragraph should be roughly balanced in length
            - The explanation MUST be data-driven and tied directly to the input values
            - Avoid generic statements such as "prices may fluctuate" without context

            Paragraph 1:
            - What happened (price movement, trend, volatility, volume)
            - Explain how the combination of trend, volatility, and volume reflects investor behavior
            - Explicitly reference price consistency and price range (high/low)

            Paragraph 2:
            - What this means for a beginner investor
            - Include a conditional risk explanation based ONLY on the input data (e.g., what happens if volume weakens or consistency changes)
            - Avoid generic risk statements
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
        String userContent = stockAnalysisService.buildPromptUserContent(snapshot);
        OpenAiExplanationResult result = openAiClient.generateExplanation(SYSTEM_PROMPT, userContent);

        if (!result.success()) {
            return new StockExplanationResponse(
                    snapshot.getSymbol(),
                    snapshot.getTimeframe(),
                    UNAVAILABLE_MESSAGE,
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

        StockAiExplanation explanation = new StockAiExplanation();
        explanation.setAnalysisSnapshot(snapshot);
        explanation.setSymbol(snapshot.getSymbol());
        explanation.setTimeframe(snapshot.getTimeframe());
        explanation.setModel(model);
        explanation.setPromptVersion(PROMPT_VERSION);
        explanation.setExplanation(result.content());
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
}
