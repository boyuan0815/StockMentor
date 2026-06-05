package net.boyuan.stockmentor.watchlist.dto;

import java.util.List;

public record WatchlistResponse(
        Long userId,
        List<WatchlistStockResponse> watchlistedStocks,
        String message
) {
}
