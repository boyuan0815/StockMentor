package net.boyuan.stockmentor.watchlist.dto;

import java.util.List;

public record WatchlistBatchRemoveRequest(
        List<String> symbols
) {
}
