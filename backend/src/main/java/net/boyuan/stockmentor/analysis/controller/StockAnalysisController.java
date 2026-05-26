package net.boyuan.stockmentor.analysis.controller;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.service.StockAiExplanationService;
import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockAnalysisController {
    private final StockAiExplanationService stockAiExplanationService;

    @GetMapping("/{symbol}/ai-explanation")
    public StockExplanationResponse getExplanation(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "7D") String timeframe
    ) {
        return stockAiExplanationService.getOrGenerateExplanation(symbol, timeframe);
    }
}
