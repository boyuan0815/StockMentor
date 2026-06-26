package net.boyuan.stockmentor.watchlist.dto;

import java.util.List;

public record WatchlistBatchRemoveResponse(
        List<String> removedSymbols,
        List<String> notFoundSymbols,
        List<WatchlistStockResponse> remainingWatchlistedStocks,
        String message
) {
}
