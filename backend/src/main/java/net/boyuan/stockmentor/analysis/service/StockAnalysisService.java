package net.boyuan.stockmentor.analysis.service;

import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;

public interface StockAnalysisService {
    StockAnalysisSnapshot createOrReuseSnapshot(String symbol, String timeframe);

    String buildPromptUserContent(StockAnalysisSnapshot snapshot);
}
