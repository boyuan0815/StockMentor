package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import net.boyuan.stockmentor.ai.entity.StockAiExplanation;
import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.service.OpenAiClient;
import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockAiExplanationServiceImplTests {
    @Mock
    private StockAnalysisService stockAnalysisService;
    @Mock
    private StockAiExplanationRepository explanationRepository;
    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private DelayedMarketPriceService delayedMarketPriceService;

    private StockAiExplanationServiceImpl service;
    private StockAnalysisSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new StockAiExplanationServiceImpl(
                stockAnalysisService,
                explanationRepository,
                openAiClient,
                new ObjectMapper().findAndRegisterModules(),
                delayedMarketPriceService
        );

        snapshot = new StockAnalysisSnapshot();
        snapshot.setAnalysisSnapshotId(11L);
        snapshot.setSymbol("NVDA");
        snapshot.setTimeframe("7D");
        snapshot.setDataStartDate(LocalDate.of(2026, 6, 1));
        snapshot.setDataEndDate(LocalDate.of(2026, 6, 7));
        snapshot.setDataSource("daily");
        snapshot.setIsFallback(false);
        snapshot.setBaselineRiskCategory("AGGRESSIVE");
        snapshot.setRiskCategory("AGGRESSIVE");

        org.mockito.Mockito.lenient().when(stockAnalysisService.createOrReuseSnapshot("NVDA", "7D")).thenReturn(snapshot);
        when(openAiClient.getModel()).thenReturn("test-model");
        when(explanationRepository.findByAnalysisSnapshotAndModelAndPromptVersion(eq(snapshot), eq("test-model"), anyString()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(explanationRepository.save(any(StockAiExplanation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(stockAnalysisService.buildPromptUserContent(snapshot)).thenReturn("prompt");
    }

    @Test
    void malformedStructuredExplanationIsUnavailableAndNotSaved() {
        when(openAiClient.generateExplanation(anyString(), eq("prompt")))
                .thenReturn(new OpenAiExplanationResult(
                        true,
                        "{\"explanation\":\"\",\"highlights\":[]}",
                        10,
                        20,
                        30,
                        "stop",
                        null
                ));

        StockExplanationResponse response = service.getOrGenerateExplanation("NVDA", "7D");

        assertFalse(response.available());
        assertTrue(response.explanation().contains("currently unavailable"));
        verify(explanationRepository, never()).save(any(StockAiExplanation.class));
    }

    @Test
    void oneDayExplanationUsesVisibleQuoteFieldsInPrompt() {
        snapshot.setTimeframe("1D");
        snapshot.setCurrentPrice(new BigDecimal("101.00"));
        snapshot.setPercentChange(new BigDecimal("9.50"));
        snapshot.setTrend("uptrend");
        snapshot.setVolatilityLabel("moderate");
        snapshot.setVolumeTrend("steady");
        snapshot.setHighPrice(new BigDecimal("105.00"));
        snapshot.setLowPrice(new BigDecimal("99.00"));
        snapshot.setPriceConsistency("steady upward movement");
        when(stockAnalysisService.createOrReuseSnapshot("NVDA", "1D")).thenReturn(snapshot);
        when(delayedMarketPriceService.resolveForDisplay("NVDA")).thenReturn(new DelayedMarketPrice(
                "NVDA",
                new BigDecimal("100.00"),
                new BigDecimal("4.12"),
                LocalDateTime.of(2026, 6, 29, 15, 45),
                LocalDateTime.of(2026, 6, 29, 15, 45),
                15,
                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                true,
                true,
                "Delayed educational quote.",
                "stock_price_history_1min",
                "America/New_York",
                LocalDateTime.of(2026, 6, 29, 16, 0),
                LocalDate.of(2026, 6, 29),
                new BigDecimal("96.05"),
                new BigDecimal("3.95"),
                "Delayed 15 min"
        ));
        when(openAiClient.generateExplanation(anyString(), anyString()))
                .thenReturn(new OpenAiExplanationResult(
                        true,
                        "{\"explanation\":\"NVDA shows a visible quote change that may suggest a steady move. Watch whether volume stays steady next.\",\"highlights\":[]}",
                        10,
                        20,
                        30,
                        "stop",
                        null
                ));

        StockExplanationResponse response = service.getOrGenerateExplanation("NVDA", "1D");

        assertTrue(response.available());
        verify(openAiClient).generateExplanation(anyString(), argThat(prompt ->
                prompt.contains("Visible quote price: 100")
                        && prompt.contains("Visible quote percent change vs previous close: +4.12%")
                        && !prompt.contains("1D change: +9.5%")
        ));
    }
}
