package net.boyuan.stockmentor.watchlist.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.watchlist.dto.*;
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
    private final DelayedMarketPriceService delayedMarketPriceService;

    @Override
    @Transactional(readOnly = true)
    public WatchlistResponse getCurrentUserWatchlist() {
        AppUser user = currentUserService.getCurrentUser();
        List<String> symbols = orderedWatchlistRows(user.getUserId()).stream()
                .map(UserWatchlist::getSymbol)
                .filter(SUPPORTED_SYMBOLS::contains)
                .distinct()
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
        List<UserWatchlist> existingRows = orderedWatchlistRows(user.getUserId());
        normalizeOrders(existingRows);
        UserWatchlist watchlist = new UserWatchlist();
        watchlist.setUser(user);
        watchlist.setSymbol(normalizedSymbol);
        watchlist.setDisplayOrder(existingRows.size());
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
        normalizeOrders(orderedWatchlistRows(user.getUserId()).stream()
                .filter(row -> !Objects.equals(row.getWatchlistId(), existing.get().getWatchlistId()))
                .toList());
        return new WatchlistActionResponse(
                "Stock removed from watchlist",
                true,
                toStockResponse(normalizedSymbol, loadStock(normalizedSymbol), false)
        );
    }

    @Override
    @Transactional
    public WatchlistResponse reorderCurrentUserWatchlist(List<String> symbols) {
        AppUser user = currentUserService.getCurrentUser();
        List<UserWatchlist> rows = orderedWatchlistRows(user.getUserId());
        List<String> requestedSymbols = normalizeSymbols(symbols);
        validateNoDuplicates(requestedSymbols);
        Map<String, UserWatchlist> rowBySymbol = rows.stream()
                .collect(Collectors.toMap(UserWatchlist::getSymbol, Function.identity(), (first, second) -> first));
        Set<String> ownedSymbols = rowBySymbol.keySet();
        if (!ownedSymbols.equals(new LinkedHashSet<>(requestedSymbols))) {
            throw new IllegalArgumentException("Watchlist reorder must include every current watchlist symbol exactly once");
        }
        for (int index = 0; index < requestedSymbols.size(); index++) {
            UserWatchlist row = rowBySymbol.get(requestedSymbols.get(index));
            row.setDisplayOrder(index);
            row.setUpdatedAt(LocalDateTime.now());
        }
        watchlistRepository.saveAll(rows);
        return getCurrentUserWatchlist();
    }

    @Override
    @Transactional
    public WatchlistBatchRemoveResponse batchRemoveFromCurrentUserWatchlist(List<String> symbols) {
        AppUser user = currentUserService.getCurrentUser();
        List<String> requestedSymbols = normalizeSymbols(symbols);
        validateNoDuplicates(requestedSymbols);
        List<UserWatchlist> rows = orderedWatchlistRows(user.getUserId());
        Map<String, UserWatchlist> rowBySymbol = rows.stream()
                .collect(Collectors.toMap(UserWatchlist::getSymbol, Function.identity(), (first, second) -> first));
        List<String> removedSymbols = new ArrayList<>();
        List<String> notFoundSymbols = new ArrayList<>();
        for (String symbol : requestedSymbols) {
            UserWatchlist row = rowBySymbol.get(symbol);
            if (row == null) {
                notFoundSymbols.add(symbol);
                continue;
            }
            watchlistRepository.delete(row);
            removedSymbols.add(symbol);
        }
        List<UserWatchlist> remainingRows = rows.stream()
                .filter(row -> !removedSymbols.contains(row.getSymbol()))
                .toList();
        normalizeOrders(remainingRows);
        Map<String, Stock> stockBySymbol = loadStocks(remainingRows.stream().map(UserWatchlist::getSymbol).toList());
        List<WatchlistStockResponse> remainingStocks = remainingRows.stream()
                .map(UserWatchlist::getSymbol)
                .map(symbol -> toStockResponse(symbol, stockBySymbol.get(symbol), true))
                .toList();
        return new WatchlistBatchRemoveResponse(
                removedSymbols,
                notFoundSymbols,
                remainingStocks,
                "Batch watchlist removal completed"
        );
    }

    private List<UserWatchlist> orderedWatchlistRows(Long userId) {
        List<UserWatchlist> rows = watchlistRepository.findByUserUserIdOrderByDisplayOrderAscCreatedAtAscWatchlistIdAsc(userId);
        if (rows == null || rows.isEmpty()) {
            rows = watchlistRepository.findByUserUserId(userId);
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing((UserWatchlist row) -> row.getDisplayOrder() == null ? Integer.MAX_VALUE : row.getDisplayOrder())
                        .thenComparing(UserWatchlist::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(UserWatchlist::getWatchlistId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private void normalizeOrders(List<UserWatchlist> rows) {
        for (int index = 0; index < rows.size(); index++) {
            UserWatchlist row = rows.get(index);
            if (!Objects.equals(row.getDisplayOrder(), index)) {
                row.setDisplayOrder(index);
                row.setUpdatedAt(LocalDateTime.now());
                watchlistRepository.save(row);
            }
        }
    }

    private List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            throw new IllegalArgumentException("Watchlist symbols are required");
        }
        return symbols.stream()
                .map(this::validateSupportedSymbol)
                .toList();
    }

    private void validateNoDuplicates(List<String> symbols) {
        if (new HashSet<>(symbols).size() != symbols.size()) {
            throw new IllegalArgumentException("Duplicate watchlist symbols are not allowed");
        }
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
        DelayedMarketPrice delayedPrice = delayedMarketPriceService.resolveForDisplay(symbol);
        return new WatchlistStockResponse(
                stock == null ? null : stock.getStockId(),
                symbol,
                stock == null ? StockMetadata.COMPANY_MAP.getOrDefault(symbol, symbol) : stock.getCompanyName(),
                snapshot == null ? (stock == null ? null : stock.getCurrentPrice()) : snapshot.getCurrentPrice(),
                snapshot == null ? (stock == null ? null : stock.getPercentChange()) : snapshot.getPercentChange(),
                snapshot == null ? null : snapshot.getTrend(),
                snapshot == null ? null : snapshot.getVolatilityLabel(),
                snapshot == null ? StockMetadata.RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate") : snapshot.getRiskCategory(),
                isWatchlisted,
                delayedPrice == null ? null : delayedPrice.previousClose(),
                delayedPrice == null ? null : delayedPrice.displayedAbsoluteChange(),
                delayedPrice == null ? null : delayedPrice.displayedPrice(),
                delayedPrice == null ? null : delayedPrice.displayedPercentChange(),
                delayedPrice == null ? null : delayedPrice.displayedMarketTime(),
                delayedPrice == null ? null : delayedPrice.targetDisplayMarketTime(),
                delayedPrice == null ? null : delayedPrice.dataDelayMinutes(),
                delayedPrice == null ? null : delayedPrice.priceFreshnessStatusName(),
                delayedPrice == null ? null : delayedPrice.priceFreshnessLabel(),
                delayedPrice == null ? null : delayedPrice.priceAvailable(),
                delayedPrice == null ? null : delayedPrice.tradeExecutable(),
                delayedPrice == null ? null : delayedPrice.dataNote(),
                delayedPrice == null ? null : delayedPrice.priceSource(),
                delayedPrice == null ? null : delayedPrice.marketTimeZone(),
                delayedPrice == null ? null : delayedPrice.lastBackendUpdatedAt()
        );
    }
}
