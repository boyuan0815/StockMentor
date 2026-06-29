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
import net.boyuan.stockmentor.market.stock.model.DelayedIntradayHistorySelection;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.StockMarketDataService;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.IntradayDayRangeProjection;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockMarketDataServiceImpl implements StockMarketDataService {
    private static final String ANALYSIS_TIMEFRAME = "7D";
    private static final String INTRADAY_TIMEFRAME = "1D";
    private static final String FIVE_DAY_INTRADAY_TIMEFRAME = "5D";
    private static final String DAILY_TIMEFRAME = "7D";
    private static final String ONE_MONTH_TIMEFRAME = "1M";
    private static final String THREE_MONTH_TIMEFRAME = "3M";
    private static final String YEAR_TO_DATE_TIMEFRAME = "YTD";
    private static final String ONE_YEAR_TIMEFRAME = "1Y";
    private static final String AI_EXPLANATION_PROMPT_VERSION = "stock-explanation-v1";
    private static final LocalTime REGULAR_SESSION_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_SESSION_CLOSE = LocalTime.of(16, 0);
    private static final int REGULAR_SESSION_POINT_COUNT = 390;
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
    private final DelayedMarketPriceService delayedMarketPriceService;

    @Value("${openai.model:gpt-5-mini}")
    private String openAiModel = "gpt-5-mini";

    @Override
    @Transactional(readOnly = true)
    public StockListResponse getStocksForCurrentUser() {
        AppUser user = currentUserService.getCurrentUser();
        Map<String, Stock> stockBySymbol = loadStocks(SUPPORTED_SYMBOLS);
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = loadLatestSnapshots(SUPPORTED_SYMBOLS);
        Set<String> watchlistedSymbols = loadWatchlistedSymbols(user.getUserId(), SUPPORTED_SYMBOLS);
        Map<String, DelayedMarketPrice> delayedPriceBySymbol = loadDelayedPrices(SUPPORTED_SYMBOLS);

        List<StockListItemResponse> stocks = SUPPORTED_SYMBOLS.stream()
                .map(symbol -> toListItem(
                        symbol,
                        stockBySymbol.get(symbol),
                        snapshotBySymbol.get(symbol),
                        watchlistedSymbols.contains(symbol),
                        delayedPriceBySymbol.get(symbol)))
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
                .findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(normalizedSymbol,
                        ANALYSIS_TIMEFRAME)
                .orElse(null);
        boolean watchlisted = watchlistRepository.existsByUserUserIdAndSymbol(user.getUserId(), normalizedSymbol);
        boolean explanationAvailable = isAiExplanationAvailable(snapshot);
        DelayedMarketPrice delayedPrice = delayedMarketPriceService.resolveForDisplay(normalizedSymbol);

        return toDetail(normalizedSymbol, stock, snapshot, watchlisted, explanationAvailable, delayedPrice);
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
        if (FIVE_DAY_INTRADAY_TIMEFRAME.equals(normalizedTimeframe)) {
            return getFiveDayIntradayHistory(normalizedSymbol,
                    delayedMarketPriceService.resolveForDisplay(normalizedSymbol));
        }
        if (DAILY_TIMEFRAME.equals(normalizedTimeframe)) {
            return getLatestDailyHistory(normalizedSymbol,
                    delayedMarketPriceService.resolveForDisplay(normalizedSymbol));
        }
        return getDailyRangeHistory(normalizedSymbol, normalizedTimeframe,
                delayedMarketPriceService.resolveForDisplay(normalizedSymbol));
    }

    private StockHistoryResponse getIntradayHistory(String symbol) {
        DelayedIntradayHistorySelection selection = delayedMarketPriceService.loadOneDayHistoryForDisplay(symbol);
        List<StockHistoryPointResponse> points = selection.rows()
                .stream()
                .filter(this::isRegularSessionHistory)
                .map(this::toHistoryPoint)
                .toList();

        String message = points.isEmpty()
                ? "No stored delayed intraday history is available for " + symbol
                : "Stored intraday history returned at or before the displayed market cutoff";
        return stockHistoryResponse(
                symbol,
                INTRADAY_TIMEFRAME,
                "stock_price_history_1min",
                points,
                message,
                selection.metadata(),
                includedTradingDays(points),
                1,
                intradayExpectedPointCount(expectedTradingDates(selection.metadata(), points), selection.metadata(),
                        1));
    }

    private StockHistoryResponse getFiveDayIntradayHistory(String symbol, DelayedMarketPrice delayedPrice) {
        List<LocalDate> latestDates = new ArrayList<>(
                historyRepository.findLatestTradingDates(symbol, "1min", PageRequest.of(0, 5)));
        Collections.reverse(latestDates);
        List<StockHistoryPointResponse> points = latestDates.isEmpty()
                ? List.of()
                : historyRepository
                        .findBySymbolAndTradingDateInAndTimeIntervalOrderByTimestampAsc(symbol, latestDates, "1min")
                        .stream()
                        .filter(this::isRegularSessionHistory)
                        .map(this::toHistoryPoint)
                        .toList();

        String message = points.isEmpty()
                ? "No stored 5D intraday history is available for " + symbol
                : "Stored 5D intraday history returned";
        return stockHistoryResponse(
                symbol,
                FIVE_DAY_INTRADAY_TIMEFRAME,
                "stock_price_history_1min",
                points,
                message,
                delayedPrice,
                latestDates.size(),
                5,
                intradayExpectedPointCount(latestDates, delayedPrice, 5));
    }

    private StockHistoryResponse getLatestDailyHistory(String symbol, DelayedMarketPrice delayedPrice) {
        List<StockPriceDaily> dailyRows = new ArrayList<>(
                dailyRepository.findBySymbolOrderByTradingDateDesc(symbol, PageRequest.of(0, 7)));
        Collections.reverse(dailyRows);

        List<StockHistoryPointResponse> points = dailyRows.stream()
                .map(this::toHistoryPoint)
                .toList();

        String message = points.isEmpty()
                ? "No stored daily history is available for " + symbol
                : "Stored daily history returned";
        return stockHistoryResponse(symbol, DAILY_TIMEFRAME, "stock_price_daily", points, message, delayedPrice);
    }

    private StockHistoryResponse getDailyRangeHistory(String symbol, String timeframe,
            DelayedMarketPrice delayedPrice) {
        Optional<StockPriceDaily> latestDaily = dailyRepository.findTopBySymbolOrderByTradingDateDesc(symbol);
        if (latestDaily.isEmpty()) {
            return stockHistoryResponse(
                    symbol,
                    timeframe,
                    "stock_price_daily",
                    List.of(),
                    "No stored daily history is available for " + symbol,
                    delayedPrice);
        }

        LocalDate latestDate = latestDaily.get().getTradingDate();
        LocalDate startDate = calculateDailyRangeStartDate(timeframe, latestDate);
        List<StockHistoryPointResponse> points = dailyRepository
                .findBySymbolAndTradingDateBetweenOrderByTradingDateAsc(symbol, startDate, latestDate)
                .stream()
                .map(this::toHistoryPoint)
                .toList();

        String message = points.isEmpty()
                ? "No stored daily history is available for " + symbol
                : "Stored daily history returned from " + startDate + " to " + latestDate;
        return stockHistoryResponse(symbol, timeframe, "stock_price_daily", points, message, delayedPrice);
    }

    private LocalDate calculateDailyRangeStartDate(String timeframe, LocalDate latestDate) {
        return switch (timeframe) {
            case ONE_MONTH_TIMEFRAME -> latestDate.minusMonths(1);
            case THREE_MONTH_TIMEFRAME -> latestDate.minusMonths(3);
            case YEAR_TO_DATE_TIMEFRAME -> LocalDate.of(latestDate.getYear(), 1, 1);
            case ONE_YEAR_TIMEFRAME -> latestDate.minusYears(1);
            default -> throw new IllegalArgumentException("Unsupported stock history timeframe: " + timeframe);
        };
    }

    private StockListItemResponse toListItem(
            String symbol,
            Stock stock,
            StockAnalysisSnapshot snapshot,
            boolean isWatchlisted,
            DelayedMarketPrice delayedPrice) {
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
                delayedPrice == null ? null : delayedPrice.lastBackendUpdatedAt());
    }

    private StockDetailResponse toDetail(
            String symbol,
            Stock stock,
            StockAnalysisSnapshot snapshot,
            boolean isWatchlisted,
            boolean aiExplanationAvailable,
            DelayedMarketPrice delayedPrice) {
        DayRange dayRange = displayedDayRange(symbol, delayedPrice);
        DisplayedQuoteContext quoteContext = displayedQuoteContext(symbol, delayedPrice);

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
                dayRange.highPrice(),
                dayRange.lowPrice(),
                snapshot == null ? null : snapshot.getDataSource(),
                snapshot == null ? null : snapshot.getDataSource(),
                snapshot == null ? null : snapshot.getIsFallback(),
                snapshot == null ? null : snapshot.getMissingDataCount(),
                snapshot == null ? null : snapshot.getAnalysisSnapshotId(),
                snapshot == null ? null : snapshot.getSnapshotHash(),
                isWatchlisted,
                aiExplanationAvailable,
                "/api/stocks/" + symbol + "/ai-explanation?timeframe=" + ANALYSIS_TIMEFRAME,
                true,
                quoteContext.previousClose(),
                quoteContext.displayedAbsoluteChange(),
                quoteContext.displayedVolume(),
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
                delayedPrice == null ? null : delayedPrice.lastBackendUpdatedAt(),
                snapshot == null ? null : snapshot.getHighPrice(),
                snapshot == null ? null : snapshot.getLowPrice(),
                snapshot == null ? null : snapshot.getTimeframe());
    }

    private DayRange displayedDayRange(String symbol, DelayedMarketPrice delayedPrice) {
        if (delayedPrice == null || delayedPrice.priceSource() == null || delayedPrice.tradingDate() == null) {
            return DayRange.empty();
        }
        if (DelayedMarketPriceService.DAILY_PRICE_SOURCE.equals(delayedPrice.priceSource())) {
            return dailyRepository.findBySymbolAndTradingDate(symbol, delayedPrice.tradingDate())
                    .map(daily -> new DayRange(daily.getHighPrice(), daily.getLowPrice()))
                    .orElseGet(DayRange::empty);
        }
        if (DelayedMarketPriceService.INTRADAY_PRICE_SOURCE.equals(delayedPrice.priceSource())) {
            LocalDateTime cutoff = delayedPrice.displayedMarketTime() == null
                    ? delayedPrice.targetDisplayMarketTime()
                    : delayedPrice.displayedMarketTime();
            if (cutoff == null) {
                return DayRange.empty();
            }
            IntradayDayRangeProjection range = historyRepository.findDayRangeAtOrBefore(
                    symbol,
                    delayedPrice.tradingDate(),
                    "1min",
                    cutoff);
            if (range == null || range.getHighPrice() == null || range.getLowPrice() == null) {
                return DayRange.empty();
            }
            return new DayRange(range.getHighPrice(), range.getLowPrice());
        }
        return DayRange.empty();
    }

    private DisplayedQuoteContext displayedQuoteContext(String symbol, DelayedMarketPrice delayedPrice) {
        if (delayedPrice == null || delayedPrice.tradingDate() == null) {
            return DisplayedQuoteContext.empty();
        }

        return new DisplayedQuoteContext(
                delayedPrice.previousClose(),
                delayedPrice.displayedAbsoluteChange(),
                displayedVolume(symbol, delayedPrice));
    }

    private Long displayedVolume(String symbol, DelayedMarketPrice delayedPrice) {
        if (DelayedMarketPriceService.INTRADAY_PRICE_SOURCE.equals(delayedPrice.priceSource())) {
            LocalDateTime cutoff = delayedPrice.displayedMarketTime() == null
                    ? delayedPrice.targetDisplayMarketTime()
                    : delayedPrice.displayedMarketTime();
            if (cutoff != null) {
                Long intradayVolume = historyRepository.sumVolumeAtOrBefore(
                        symbol,
                        delayedPrice.tradingDate(),
                        "1min",
                        cutoff);
                if (intradayVolume != null && intradayVolume > 0) {
                    return intradayVolume;
                }
            }
        }

        if (DelayedMarketPriceService.DAILY_PRICE_SOURCE.equals(delayedPrice.priceSource())) {
            return dailyRepository.findBySymbolAndTradingDate(symbol, delayedPrice.tradingDate())
                    .map(StockPriceDaily::getVolume)
                    .orElse(null);
        }

        return null;
    }

    private StockHistoryResponse stockHistoryResponse(
            String symbol,
            String timeframe,
            String source,
            List<StockHistoryPointResponse> points,
            String message,
            DelayedMarketPrice delayedPrice) {
        int requestedTradingDays = switch (timeframe) {
            case INTRADAY_TIMEFRAME -> 1;
            case FIVE_DAY_INTRADAY_TIMEFRAME -> 5;
            case DAILY_TIMEFRAME -> 7;
            default -> points == null ? 0 : points.size();
        };
        int includedTradingDays = (int) (points == null ? 0
                : points.stream()
                        .map(StockHistoryPointResponse::tradingDate)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count());
        return stockHistoryResponse(symbol, timeframe, source, points, message, delayedPrice, includedTradingDays,
                requestedTradingDays);
    }

    private StockHistoryResponse stockHistoryResponse(
            String symbol,
            String timeframe,
            String source,
            List<StockHistoryPointResponse> points,
            String message,
            DelayedMarketPrice delayedPrice,
            int includedTradingDays,
            int requestedTradingDays) {
        boolean intraday = DelayedMarketPriceService.INTRADAY_PRICE_SOURCE.equals(source);
        int actualPointCount = points == null ? 0 : points.size();
        int expectedPointCount = intraday ? Math.max(0, requestedTradingDays) * REGULAR_SESSION_POINT_COUNT
                : Math.max(0, requestedTradingDays);
        return stockHistoryResponse(
                symbol,
                timeframe,
                source,
                points,
                message,
                delayedPrice,
                includedTradingDays,
                requestedTradingDays,
                expectedPointCount);
    }

    private StockHistoryResponse stockHistoryResponse(
            String symbol,
            String timeframe,
            String source,
            List<StockHistoryPointResponse> points,
            String message,
            DelayedMarketPrice delayedPrice,
            int includedTradingDays,
            int requestedTradingDays,
            int expectedPointCount) {
        boolean intraday = DelayedMarketPriceService.INTRADAY_PRICE_SOURCE.equals(source);
        int actualPointCount = points == null ? 0 : points.size();
        int missingDataCount = Math.max(0, expectedPointCount - actualPointCount);
        boolean candlestickSupported = points != null && !points.isEmpty()
                && points.stream().allMatch(point -> point.openPrice() != null
                        && point.highPrice() != null
                        && point.lowPrice() != null
                        && point.closePrice() != null);
        String completenessNote = missingDataCount == 0
                ? "Stored history is complete for the expected regular-session point count."
                : "Stored history is incomplete; missing points were not synthesized.";
        return new StockHistoryResponse(
                symbol,
                timeframe,
                source,
                points,
                message,
                delayedPrice == null ? null : delayedPrice.displayedPrice(),
                delayedPrice == null ? null : delayedPrice.displayedPercentChange(),
                delayedPrice == null ? null : delayedPrice.previousClose(),
                delayedPrice == null ? null : delayedPrice.displayedAbsoluteChange(),
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
                intraday ? "INTRADAY_1MIN" : "DAILY",
                actualPointCount > 0,
                candlestickSupported,
                expectedPointCount,
                actualPointCount,
                missingDataCount,
                includedTradingDays,
                requestedTradingDays,
                "America/New_York",
                source,
                false,
                completenessNote);
    }

    private boolean isRegularSessionHistory(StockPriceHistory history) {
        if (history == null || history.getTimestamp() == null) {
            return false;
        }
        LocalTime time = history.getTimestamp().toLocalTime();
        return !time.isBefore(REGULAR_SESSION_OPEN) && time.isBefore(REGULAR_SESSION_CLOSE);
    }

    private int includedTradingDays(List<StockHistoryPointResponse> points) {
        return (int) (points == null ? 0
                : points.stream()
                        .map(StockHistoryPointResponse::tradingDate)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count());
    }

    private List<LocalDate> expectedTradingDates(DelayedMarketPrice delayedPrice,
            List<StockHistoryPointResponse> points) {
        if (delayedPrice != null && delayedPrice.tradingDate() != null) {
            return List.of(delayedPrice.tradingDate());
        }
        return points == null ? List.of()
                : points.stream()
                        .map(StockHistoryPointResponse::tradingDate)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
    }

    private int intradayExpectedPointCount(List<LocalDate> selectedTradingDates, DelayedMarketPrice delayedPrice,
            int requestedTradingDays) {
        int requested = Math.max(0, requestedTradingDays);
        if (requested == 0) {
            return 0;
        }
        if (delayedPrice == null
                || delayedPrice.tradingDate() == null
                || delayedPrice.targetDisplayMarketTime() == null
                || selectedTradingDates == null
                || !selectedTradingDates.contains(delayedPrice.tradingDate())) {
            return requested * REGULAR_SESSION_POINT_COUNT;
        }
        int partialCurrentDayCount = regularSessionExpectedRowsThrough(
                delayedPrice.targetDisplayMarketTime().toLocalTime());
        return Math.max(0, requested - 1) * REGULAR_SESSION_POINT_COUNT + partialCurrentDayCount;
    }

    private int regularSessionExpectedRowsThrough(LocalTime cutoffTime) {
        if (cutoffTime == null || cutoffTime.isBefore(REGULAR_SESSION_OPEN)) {
            return 0;
        }
        if (!cutoffTime.isBefore(REGULAR_SESSION_CLOSE)) {
            return REGULAR_SESSION_POINT_COUNT;
        }
        int openMinute = REGULAR_SESSION_OPEN.getHour() * 60 + REGULAR_SESSION_OPEN.getMinute();
        int cutoffMinute = cutoffTime.getHour() * 60 + cutoffTime.getMinute();
        return Math.min(REGULAR_SESSION_POINT_COUNT, Math.max(0, cutoffMinute - openMinute + 1));
    }

    private boolean isAiExplanationAvailable(StockAnalysisSnapshot snapshot) {
        return snapshot != null && explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                snapshot,
                openAiModel,
                AI_EXPLANATION_PROMPT_VERSION);
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
                history.getSource());
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
                daily.getSource());
    }

    private Map<String, Stock> loadStocks(Collection<String> symbols) {
        return stockRepository.findBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
    }

    private Map<String, StockAnalysisSnapshot> loadLatestSnapshots(Collection<String> symbols) {
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = new LinkedHashMap<>();
        snapshotRepository
                .findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(symbols, ANALYSIS_TIMEFRAME)
                .forEach(snapshot -> snapshotBySymbol.putIfAbsent(snapshot.getSymbol(), snapshot));
        return snapshotBySymbol;
    }

    private Set<String> loadWatchlistedSymbols(Long userId, Collection<String> symbols) {
        return watchlistRepository.findByUserUserIdAndSymbolIn(userId, symbols).stream()
                .map(UserWatchlist::getSymbol)
                .collect(Collectors.toSet());
    }

    private Map<String, DelayedMarketPrice> loadDelayedPrices(Collection<String> symbols) {
        return symbols.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        delayedMarketPriceService::resolveForDisplay,
                        (first, second) -> first,
                        LinkedHashMap::new));
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
        if (!INTRADAY_TIMEFRAME.equals(normalizedTimeframe)
                && !FIVE_DAY_INTRADAY_TIMEFRAME.equals(normalizedTimeframe)
                && !DAILY_TIMEFRAME.equals(normalizedTimeframe)
                && !ONE_MONTH_TIMEFRAME.equals(normalizedTimeframe)
                && !THREE_MONTH_TIMEFRAME.equals(normalizedTimeframe)
                && !YEAR_TO_DATE_TIMEFRAME.equals(normalizedTimeframe)
                && !ONE_YEAR_TIMEFRAME.equals(normalizedTimeframe)) {
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

    private record DayRange(BigDecimal highPrice, BigDecimal lowPrice) {
        static DayRange empty() {
            return new DayRange(null, null);
        }
    }

    private record DisplayedQuoteContext(
            BigDecimal previousClose,
            BigDecimal displayedAbsoluteChange,
            Long displayedVolume) {
        static DisplayedQuoteContext empty() {
            return new DisplayedQuoteContext(null, null, null);
        }
    }
}
