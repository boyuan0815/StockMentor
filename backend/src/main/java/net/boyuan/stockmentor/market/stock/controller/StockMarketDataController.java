package net.boyuan.stockmentor.market.stock.controller;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.market.stock.dto.StockDetailResponse;
import net.boyuan.stockmentor.market.stock.dto.StockHistoryResponse;
import net.boyuan.stockmentor.market.stock.dto.StockListResponse;
import net.boyuan.stockmentor.market.stock.service.StockMarketDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockMarketDataController {
    private final StockMarketDataService stockMarketDataService;

    @GetMapping
    public StockListResponse getStocks() {
        return stockMarketDataService.getStocksForCurrentUser();
    }

    @GetMapping("/{symbol}")
    public StockDetailResponse getStockDetail(@PathVariable String symbol) {
        return stockMarketDataService.getStockDetailForCurrentUser(symbol);
    }

    @GetMapping("/{symbol}/history")
    public StockHistoryResponse getStockHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1D") String timeframe
    ) {
        return stockMarketDataService.getStockHistoryForCurrentUser(symbol, timeframe);
    }
}
