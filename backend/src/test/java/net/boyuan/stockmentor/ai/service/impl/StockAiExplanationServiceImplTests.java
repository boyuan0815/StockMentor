package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import net.boyuan.stockmentor.ai.entity.StockAiExplanation;
import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.service.OpenAiClient;
import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private StockAiExplanationServiceImpl service;
    private StockAnalysisSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new StockAiExplanationServiceImpl(
                stockAnalysisService,
                explanationRepository,
                openAiClient,
                new ObjectMapper().findAndRegisterModules()
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

        when(stockAnalysisService.createOrReuseSnapshot("NVDA", "7D")).thenReturn(snapshot);
        when(openAiClient.getModel()).thenReturn("test-model");
        when(explanationRepository.findByAnalysisSnapshotAndModelAndPromptVersion(eq(snapshot), eq("test-model"), anyString()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.buildPromptUserContent(snapshot)).thenReturn("prompt");
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
}
