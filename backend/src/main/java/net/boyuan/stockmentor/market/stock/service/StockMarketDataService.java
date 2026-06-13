package net.boyuan.stockmentor.market.stock.service;

import net.boyuan.stockmentor.market.stock.dto.StockDetailResponse;
import net.boyuan.stockmentor.market.stock.dto.StockHistoryResponse;
import net.boyuan.stockmentor.market.stock.dto.StockListResponse;

public interface StockMarketDataService {
    StockListResponse getStocksForCurrentUser();

    StockDetailResponse getStockDetailForCurrentUser(String symbol);

    StockHistoryResponse getStockHistoryForCurrentUser(String symbol, String timeframe);
}
