package net.boyuan.stockmentor.market.stock.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.dto.StockDetailResponse;
import net.boyuan.stockmentor.market.stock.dto.StockHistoryPointResponse;
import net.boyuan.stockmentor.market.stock.dto.StockHistoryResponse;
import net.boyuan.stockmentor.market.stock.dto.StockListItemResponse;
import net.boyuan.stockmentor.market.stock.dto.StockListResponse;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.StockMarketDataService;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockMarketDataServiceImpl implements StockMarketDataService {
    private static final String ANALYSIS_TIMEFRAME = "7D";
    private static final String INTRADAY_TIMEFRAME = "1D";
    private static final String DAILY_TIMEFRAME = "7D";
    private static final String AI_EXPLANATION_PROMPT_VERSION = "stock-explanation-v1";
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.stream(StockMetadata.SYMBOLS.split(","))
            .map(String::trim)
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .toList();

    private final CurrentUserService currentUserService;
    private final StockRepository stockRepository;
    private final StockAnalysisSnapshotRepository snapshotRepository;
    private final StockPriceHistoryRepository historyRepository;
    private final StockPriceDailyRepository dailyRepository;
    private final UserWatchlistRepository watchlistRepository;
    private final StockAiExplanationRepository explanationRepository;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel = "gpt-4o-mini";

    @Override
    @Transactional(readOnly = true)
    public StockListResponse getStocksForCurrentUser() {
        AppUser user = currentUserService.getCurrentUser();
        Map<String, Stock> stockBySymbol = loadStocks(SUPPORTED_SYMBOLS);
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = loadLatestSnapshots(SUPPORTED_SYMBOLS);
        Set<String> watchlistedSymbols = loadWatchlistedSymbols(user.getUserId(), SUPPORTED_SYMBOLS);

        List<StockListItemResponse> stocks = SUPPORTED_SYMBOLS.stream()
                .map(symbol -> toListItem(
                        symbol,
                        stockBySymbol.get(symbol),
                        snapshotBySymbol.get(symbol),
                        watchlistedSymbols.contains(symbol)
                ))
                .toList();

        return new StockListResponse(user.getUserId(), stocks, "Stocks returned successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public StockDetailResponse getStockDetailForCurrentUser(String symbol) {
        AppUser user = currentUserService.getCurrentUser();
        String normalizedSymbol = validateSupportedSymbol(symbol);
        Stock stock = stockRepository.findBySymbol(normalizedSymbol).orElse(null);
        StockAnalysisSnapshot snapshot = snapshotRepository
                .findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(normalizedSymbol, ANALYSIS_TIMEFRAME)
                .orElse(null);
        boolean watchlisted = watchlistRepository.existsByUserUserIdAndSymbol(user.getUserId(), normalizedSymbol);
        boolean explanationAvailable = isAiExplanationAvailable(snapshot);

        return toDetail(normalizedSymbol, stock, snapshot, watchlisted, explanationAvailable);
    }

    @Override
    @Transactional(readOnly = true)
    public StockHistoryResponse getStockHistoryForCurrentUser(String symbol, String timeframe) {
        currentUserService.getCurrentUser();
        String normalizedSymbol = validateSupportedSymbol(symbol);
        String normalizedTimeframe = normalizeTimeframe(timeframe);

        if (INTRADAY_TIMEFRAME.equals(normalizedTimeframe)) {
            return getIntradayHistory(normalizedSymbol);
        }
        return getDailyHistory(normalizedSymbol);
    }

    private StockHistoryResponse getIntradayHistory(String symbol) {
        Optional<LocalDate> latestTradingDate = historyRepository.findLatestTradingDateBySymbol(symbol);
        if (latestTradingDate.isEmpty()) {
            return new StockHistoryResponse(
                    symbol,
                    INTRADAY_TIMEFRAME,
                    "stock_price_history_1min",
                    List.of(),
                    "No stored intraday history is available for " + symbol
            );
        }

        List<StockHistoryPointResponse> points = historyRepository
                .findBySymbolAndTradingDateOrderByTimestampAsc(symbol, latestTradingDate.get())
                .stream()
                .map(this::toHistoryPoint)
                .toList();

        String message = points.isEmpty()
                ? "No stored intraday history is available for " + symbol
                : "Stored intraday history returned for " + latestTradingDate.get();
        return new StockHistoryResponse(symbol, INTRADAY_TIMEFRAME, "stock_price_history_1min", points, message);
    }

    private StockHistoryResponse getDailyHistory(String symbol) {
        List<StockPriceDaily> dailyRows = new ArrayList<>(
                dailyRepository.findBySymbolOrderByTradingDateDesc(symbol, PageRequest.of(0, 7))
        );
        Collections.reverse(dailyRows);

        List<StockHistoryPointResponse> points = dailyRows.stream()
                .map(this::toHistoryPoint)
                .toList();

        String message = points.isEmpty()
                ? "No stored daily history is available for " + symbol
                : "Stored daily history returned";
        return new StockHistoryResponse(symbol, DAILY_TIMEFRAME, "stock_price_daily", points, message);
    }

    private StockListItemResponse toListItem(
            String symbol,
            Stock stock,
            StockAnalysisSnapshot snapshot,
            boolean isWatchlisted
    ) {
        return new StockListItemResponse(
                stock == null ? null : stock.getStockId(),
                symbol,
                companyName(symbol, stock),
                stock == null ? (snapshot == null ? null : snapshot.getCurrentPrice()) : stock.getCurrentPrice(),
                stock == null ? (snapshot == null ? null : snapshot.getPercentChange()) : stock.getPercentChange(),
                stock == null ? null : stock.getLastUpdated(),
                stock == null ? null : stock.getIsMarketOpen(),
                stock == null ? null : stock.getTimezone(),
                stock == null ? null : stock.getSource(),
                riskCategory(symbol, snapshot),
                baselineRiskCategory(symbol, snapshot),
                snapshot == null ? null : snapshot.getTrend(),
                snapshot == null ? null : snapshot.getVolatilityLabel(),
                snapshot == null ? null : snapshot.getVolumeTrend(),
                snapshot == null ? null : snapshot.getPriceConsistency(),
                snapshot == null ? null : snapshot.getIsFallback(),
                snapshot == null ? null : snapshot.getMissingDataCount(),
                snapshot == null ? null : snapshot.getAnalysisSnapshotId(),
                isWatchlisted
        );
    }

    private StockDetailResponse toDetail(
            String symbol,
            Stock stock,
            StockAnalysisSnapshot snapshot,
            boolean isWatchlisted,
            boolean aiExplanationAvailable
    ) {
        return new StockDetailResponse(
                stock == null ? null : stock.getStockId(),
                symbol,
                companyName(symbol, stock),
                stock == null ? (snapshot == null ? null : snapshot.getCurrentPrice()) : stock.getCurrentPrice(),
                stock == null ? (snapshot == null ? null : snapshot.getPercentChange()) : stock.getPercentChange(),
                stock == null ? null : stock.getLastUpdated(),
                stock == null ? null : stock.getIsMarketOpen(),
                stock == null ? null : stock.getTimezone(),
                stock == null ? null : stock.getSource(),
                riskCategory(symbol, snapshot),
                baselineRiskCategory(symbol, snapshot),
                snapshot == null ? null : snapshot.getTrend(),
                snapshot == null ? null : snapshot.getVolatilityLabel(),
                snapshot == null ? null : snapshot.getVolumeTrend(),
                snapshot == null ? null : snapshot.getPriceConsistency(),
                snapshot == null ? (stock == null ? null : stock.getDayHigh()) : snapshot.getHighPrice(),
                snapshot == null ? (stock == null ? null : stock.getDayLow()) : snapshot.getLowPrice(),
                snapshot == null ? null : snapshot.getDataSource(),
                snapshot == null ? null : snapshot.getIsFallback(),
                snapshot == null ? null : snapshot.getMissingDataCount(),
                snapshot == null ? null : snapshot.getAnalysisSnapshotId(),
                snapshot == null ? null : snapshot.getSnapshotHash(),
                isWatchlisted,
                aiExplanationAvailable,
                "/api/stocks/" + symbol + "/ai-explanation?timeframe=" + ANALYSIS_TIMEFRAME,
                true
        );
    }

    private boolean isAiExplanationAvailable(StockAnalysisSnapshot snapshot) {
        return snapshot != null && explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                snapshot,
                openAiModel,
                AI_EXPLANATION_PROMPT_VERSION
        );
    }

    private StockHistoryPointResponse toHistoryPoint(StockPriceHistory history) {
        return new StockHistoryPointResponse(
                history.getTimestamp(),
                history.getTradingDate(),
                history.getOpenPrice(),
                history.getHighPrice(),
                history.getLowPrice(),
                history.getClosePrice(),
                history.getVolume(),
                history.getSource()
        );
    }

    private StockHistoryPointResponse toHistoryPoint(StockPriceDaily daily) {
        return new StockHistoryPointResponse(
                null,
                daily.getTradingDate(),
                daily.getOpenPrice(),
                daily.getHighPrice(),
                daily.getLowPrice(),
                daily.getClosePrice(),
                daily.getVolume(),
                daily.getSource()
        );
    }

    private Map<String, Stock> loadStocks(Collection<String> symbols) {
        return stockRepository.findBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
    }

    private Map<String, StockAnalysisSnapshot> loadLatestSnapshots(Collection<String> symbols) {
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = new LinkedHashMap<>();
        snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(symbols, ANALYSIS_TIMEFRAME)
                .forEach(snapshot -> snapshotBySymbol.putIfAbsent(snapshot.getSymbol(), snapshot));
        return snapshotBySymbol;
    }

    private Set<String> loadWatchlistedSymbols(Long userId, Collection<String> symbols) {
        return watchlistRepository.findByUserUserIdAndSymbolIn(userId, symbols).stream()
                .map(UserWatchlist::getSymbol)
                .collect(Collectors.toSet());
    }

    private String validateSupportedSymbol(String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SYMBOLS.contains(normalizedSymbol)) {
            throw new IllegalArgumentException("Unsupported stock symbol: " + symbol);
        }
        return normalizedSymbol;
    }

    private String normalizeTimeframe(String timeframe) {
        String normalizedTimeframe = timeframe == null ? "" : timeframe.trim().toUpperCase(Locale.ROOT);
        if (!INTRADAY_TIMEFRAME.equals(normalizedTimeframe) && !DAILY_TIMEFRAME.equals(normalizedTimeframe)) {
            throw new IllegalArgumentException("Unsupported stock history timeframe: " + timeframe);
        }
        return normalizedTimeframe;
    }

    private String companyName(String symbol, Stock stock) {
        if (stock != null && stock.getCompanyName() != null && !stock.getCompanyName().isBlank()) {
            return stock.getCompanyName();
        }
        return StockMetadata.COMPANY_MAP.getOrDefault(symbol, symbol);
    }

    private String riskCategory(String symbol, StockAnalysisSnapshot snapshot) {
        return snapshot == null
                ? StockMetadata.RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate")
                : snapshot.getRiskCategory();
    }

    private String baselineRiskCategory(String symbol, StockAnalysisSnapshot snapshot) {
        return snapshot == null
                ? StockMetadata.RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate")
                : snapshot.getBaselineRiskCategory();
    }
}
