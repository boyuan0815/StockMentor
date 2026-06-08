package net.boyuan.stockmentor.watchlist.dto;

public record WatchlistActionResponse(
        String message,
        Boolean changed,
        WatchlistStockResponse stock
) {
}
