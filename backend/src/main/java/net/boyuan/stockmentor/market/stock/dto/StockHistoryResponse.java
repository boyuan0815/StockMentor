package net.boyuan.stockmentor.market.stock.dto;

import java.util.List;

public record StockHistoryResponse(
        String symbol,
        String timeframe,
        String source,
        List<StockHistoryPointResponse> points,
        String message
) {
}
