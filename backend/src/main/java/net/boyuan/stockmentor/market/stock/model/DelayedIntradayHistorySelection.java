package net.boyuan.stockmentor.market.stock.model;

import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;

import java.util.List;

public record DelayedIntradayHistorySelection(
        List<StockPriceHistory> rows,
        DelayedMarketPrice metadata
) {
}
