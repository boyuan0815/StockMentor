package net.boyuan.stockmentor.watchlist.service;

import net.boyuan.stockmentor.watchlist.dto.WatchlistActionResponse;
import net.boyuan.stockmentor.watchlist.dto.WatchlistResponse;

public interface WatchlistService {
    WatchlistResponse getCurrentUserWatchlist();

    WatchlistActionResponse addSymbolToCurrentUserWatchlist(String symbol);

    WatchlistActionResponse removeSymbolFromCurrentUserWatchlist(String symbol);
}
