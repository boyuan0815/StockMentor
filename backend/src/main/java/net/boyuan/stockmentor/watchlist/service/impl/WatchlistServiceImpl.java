package net.boyuan.stockmentor.watchlist.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.watchlist.dto.WatchlistActionResponse;
import net.boyuan.stockmentor.watchlist.dto.WatchlistResponse;
import net.boyuan.stockmentor.watchlist.dto.WatchlistStockResponse;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import net.boyuan.stockmentor.watchlist.service.WatchlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistServiceImpl implements WatchlistService {
    private static final String ANALYSIS_TIMEFRAME = "7D";
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.stream(StockMetadata.SYMBOLS.split(","))
            .map(String::trim)
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .toList();

    private final CurrentUserService currentUserService;
    private final UserWatchlistRepository watchlistRepository;
    private final StockRepository stockRepository;
    private final StockAnalysisSnapshotRepository snapshotRepository;

    @Override
    @Transactional(readOnly = true)
    public WatchlistResponse getCurrentUserWatchlist() {
        AppUser user = currentUserService.getCurrentUser();
        List<String> symbols = watchlistRepository.findByUserUserId(user.getUserId()).stream()
                .map(UserWatchlist::getSymbol)
                .filter(SUPPORTED_SYMBOLS::contains)
                .distinct()
                .sorted(Comparator.comparingInt(SUPPORTED_SYMBOLS::indexOf))
                .toList();

        Map<String, Stock> stockBySymbol = loadStocks(symbols);
        List<WatchlistStockResponse> stocks = symbols.stream()
                .map(symbol -> toStockResponse(symbol, stockBySymbol.get(symbol), true))
                .toList();

        return new WatchlistResponse(user.getUserId(), stocks, "Watchlist returned successfully");
    }

    @Override
    @Transactional
    public WatchlistActionResponse addSymbolToCurrentUserWatchlist(String symbol) {
        AppUser user = currentUserService.getCurrentUser();
        String normalizedSymbol = validateSupportedSymbol(symbol);
        Optional<UserWatchlist> existing = watchlistRepository.findByUserUserIdAndSymbol(user.getUserId(), normalizedSymbol);
        if (existing.isPresent()) {
            return new WatchlistActionResponse(
                    "Stock is already in watchlist",
                    false,
                    toStockResponse(normalizedSymbol, loadStock(normalizedSymbol), true)
            );
        }

        LocalDateTime now = LocalDateTime.now();
        UserWatchlist watchlist = new UserWatchlist();
        watchlist.setUser(user);
        watchlist.setSymbol(normalizedSymbol);
        watchlist.setSource(WatchlistSource.MANUAL);
        watchlist.setCreatedAt(now);
        watchlist.setUpdatedAt(now);
        watchlistRepository.save(watchlist);

        return new WatchlistActionResponse(
                "Stock added to watchlist",
                true,
                toStockResponse(normalizedSymbol, loadStock(normalizedSymbol), true)
        );
    }

    @Override
    @Transactional
    public WatchlistActionResponse removeSymbolFromCurrentUserWatchlist(String symbol) {
        AppUser user = currentUserService.getCurrentUser();
        String normalizedSymbol = validateSupportedSymbol(symbol);
        Optional<UserWatchlist> existing = watchlistRepository.findByUserUserIdAndSymbol(user.getUserId(), normalizedSymbol);
        if (existing.isEmpty()) {
            return new WatchlistActionResponse(
                    "Stock was not in watchlist",
                    false,
                    toStockResponse(normalizedSymbol, loadStock(normalizedSymbol), false)
            );
        }

        watchlistRepository.delete(existing.get());
        return new WatchlistActionResponse(
                "Stock removed from watchlist",
                true,
                toStockResponse(normalizedSymbol, loadStock(normalizedSymbol), false)
        );
    }

    private String validateSupportedSymbol(String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SYMBOLS.contains(normalizedSymbol)) {
            throw new IllegalArgumentException("Unsupported watchlist symbol: " + symbol);
        }
        return normalizedSymbol;
    }

    private Map<String, Stock> loadStocks(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        return stockRepository.findBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
    }

    private Stock loadStock(String symbol) {
        return loadStocks(List.of(symbol)).get(symbol);
    }

    private WatchlistStockResponse toStockResponse(String symbol, Stock stock, boolean isWatchlisted) {
        StockAnalysisSnapshot snapshot = snapshotRepository
                .findTopBySymbolAndTimeframeOrderByCreatedAtDesc(symbol, ANALYSIS_TIMEFRAME)
                .orElse(null);
        return new WatchlistStockResponse(
                stock == null ? null : stock.getStockId(),
                symbol,
                stock == null ? StockMetadata.COMPANY_MAP.getOrDefault(symbol, symbol) : stock.getCompanyName(),
                snapshot == null ? (stock == null ? null : stock.getCurrentPrice()) : snapshot.getCurrentPrice(),
                snapshot == null ? (stock == null ? null : stock.getPercentChange()) : snapshot.getPercentChange(),
                snapshot == null ? null : snapshot.getTrend(),
                snapshot == null ? null : snapshot.getVolatilityLabel(),
                snapshot == null ? StockMetadata.RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate") : snapshot.getRiskCategory(),
                isWatchlisted
        );
    }
}
