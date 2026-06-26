package net.boyuan.stockmentor.watchlist.dto;

import java.util.List;

public record WatchlistReorderRequest(
        List<String> symbols
) {
}
