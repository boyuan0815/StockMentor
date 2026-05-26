package net.boyuan.stockmentor.ai.service;

import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;

public interface StockAiExplanationService {
    StockExplanationResponse getOrGenerateExplanation(String symbol, String timeframe);
}
