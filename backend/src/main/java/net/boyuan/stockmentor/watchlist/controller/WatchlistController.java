package net.boyuan.stockmentor.watchlist.controller;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.watchlist.dto.WatchlistActionResponse;
import net.boyuan.stockmentor.watchlist.dto.WatchlistResponse;
import net.boyuan.stockmentor.watchlist.service.WatchlistService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {
    private final WatchlistService watchlistService;

    @GetMapping
    public WatchlistResponse getWatchlist() {
        return watchlistService.getCurrentUserWatchlist();
    }

    @PostMapping("/{symbol}")
    public WatchlistActionResponse addToWatchlist(@PathVariable String symbol) {
        return watchlistService.addSymbolToCurrentUserWatchlist(symbol);
    }

    @DeleteMapping("/{symbol}")
    public WatchlistActionResponse removeFromWatchlist(@PathVariable String symbol) {
        return watchlistService.removeSymbolFromCurrentUserWatchlist(symbol);
    }
}
