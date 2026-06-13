package net.boyuan.stockmentor.market.stock.service.impl;

import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.market.stock.dto.StockDetailResponse;
import net.boyuan.stockmentor.market.stock.dto.StockHistoryResponse;
import net.boyuan.stockmentor.market.stock.dto.StockListResponse;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMarketDataServiceImplTests {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockAnalysisSnapshotRepository snapshotRepository;
    @Mock
    private StockPriceHistoryRepository historyRepository;
    @Mock
    private StockPriceDailyRepository dailyRepository;
    @Mock
    private UserWatchlistRepository watchlistRepository;
    @Mock
    private StockAiExplanationRepository explanationRepository;

    private StockMarketDataServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new StockMarketDataServiceImpl(
                currentUserService,
                stockRepository,
                snapshotRepository,
                historyRepository,
                dailyRepository,
                watchlistRepository,
                explanationRepository
        );

        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("beginner@example.com");
        user.setUsername("beginner");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);

        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of());
        lenient().when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
                anyCollection(),
                anyString()
        )).thenReturn(List.of());
        lenient().when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
                anyString(),
                anyString()
        )).thenReturn(Optional.empty());
        lenient().when(watchlistRepository.findByUserUserIdAndSymbolIn(eq(1L), anyCollection())).thenReturn(List.of());
        lenient().when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                any(StockAnalysisSnapshot.class),
                anyString(),
                anyString()
        )).thenReturn(false);
    }

    @Test
    void listReturnsOnlySupportedSymbolsInMetadataOrder() {
        StockListResponse response = service.getStocksForCurrentUser();

        assertEquals(1L, response.userId());
        assertEquals(List.of("NVDA", "TSLA", "AMD", "AAPL", "MSFT", "GOOG", "KO", "JNJ"),
                response.stocks().stream().map(stock -> stock.symbol()).toList());
    }

    @Test
    void listIncludesCurrentStoredStockPriceFieldsWhenAvailable() {
        LocalDateTime lastUpdated = LocalDateTime.of(2026, 1, 5, 16, 0);
        Stock msft = stock("MSFT", "Microsoft Corp", "422.12");
        msft.setStockId(5L);
        msft.setPercentChange(new BigDecimal("1.25"));
        msft.setLastUpdated(lastUpdated);
        msft.setIsMarketOpen(false);
        msft.setTimezone("America/New_York");
        msft.setSource("TwelveData");
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(msft));

        StockListResponse response = service.getStocksForCurrentUser();

        var item = response.stocks().stream()
                .filter(stock -> "MSFT".equals(stock.symbol()))
                .findFirst()
                .orElseThrow();
        assertEquals(5L, item.stockId());
        assertEquals("Microsoft Corp", item.companyName());
        assertEquals(new BigDecimal("422.12"), item.currentPrice());
        assertEquals(new BigDecimal("1.25"), item.percentChange());
        assertEquals(lastUpdated, item.lastUpdated());
        assertFalse(item.isMarketOpen());
        assertEquals("America/New_York", item.timezone());
        assertEquals("TwelveData", item.source());
    }

    @Test
    void listPrefersStoredStockPriceFieldsWhenSnapshotAlsoExists() {
        Stock msft = stock("MSFT", "Microsoft Corp", "422.12");
        msft.setPercentChange(new BigDecimal("1.25"));
        StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
        snapshot.setCurrentPrice(new BigDecimal("123.45"));
        snapshot.setPercentChange(new BigDecimal("2.34"));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(msft));
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
                anyCollection(),
                eq("7D")
        )).thenReturn(List.of(snapshot));

        StockListResponse response = service.getStocksForCurrentUser();

        var item = response.stocks().stream()
                .filter(stock -> "MSFT".equals(stock.symbol()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("422.12"), item.currentPrice());
        assertEquals(new BigDecimal("1.25"), item.percentChange());
        assertEquals(20L, item.latestAnalysisSnapshotId());
        assertEquals("moderate", item.riskCategory());
    }

    @Test
    void listIncludesLatestAnalysisSnapshotLabelsWhenAvailable() {
        StockAnalysisSnapshot newerSnapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
        StockAnalysisSnapshot olderSnapshot = snapshot("MSFT", 10L, "conservative", "sideways");
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
                anyCollection(),
                eq("7D")
        )).thenReturn(List.of(newerSnapshot, olderSnapshot));

        StockListResponse response = service.getStocksForCurrentUser();

        var item = response.stocks().stream()
                .filter(stock -> "MSFT".equals(stock.symbol()))
                .findFirst()
                .orElseThrow();
        assertEquals(20L, item.latestAnalysisSnapshotId());
        assertEquals("moderate", item.riskCategory());
        assertEquals("strong uptrend", item.trend());
        assertEquals("low", item.volatilityLabel());
        assertEquals("stable", item.volumeTrend());
        assertEquals("smooth upward movement", item.priceConsistency());
        assertFalse(item.isFallback());
        assertEquals(0, item.missingDataCount());
    }

    @Test
    void listHandlesMissingStockAndSnapshotSafely() {
        StockListResponse response = service.getStocksForCurrentUser();

        var item = response.stocks().stream()
                .filter(stock -> "KO".equals(stock.symbol()))
                .findFirst()
                .orElseThrow();
        assertNull(item.stockId());
        assertEquals("Coca-Cola", item.companyName());
        assertEquals("conservative", item.riskCategory());
        assertEquals("conservative", item.baselineRiskCategory());
        assertNull(item.currentPrice());
        assertNull(item.latestAnalysisSnapshotId());
    }

    @Test
    void listIncludesWatchlistStatusForCurrentUser() {
        UserWatchlist watchlist = new UserWatchlist();
        watchlist.setUser(user);
        watchlist.setSymbol("MSFT");
        when(watchlistRepository.findByUserUserIdAndSymbolIn(eq(1L), anyCollection())).thenReturn(List.of(watchlist));

        StockListResponse response = service.getStocksForCurrentUser();

        assertTrue(response.stocks().stream()
                .filter(stock -> "MSFT".equals(stock.symbol()))
                .findFirst()
                .orElseThrow()
                .isWatchlisted());
        assertFalse(response.stocks().stream()
                .filter(stock -> "KO".equals(stock.symbol()))
                .findFirst()
                .orElseThrow()
                .isWatchlisted());
    }

    @Test
    void detailReturnsSupportedSymbolAndNormalizesLowercase() {
        Stock msft = stock("MSFT", "Microsoft", "422.12");
        StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
        when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.of(msft));
        when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT", "7D"))
                .thenReturn(Optional.of(snapshot));
        when(watchlistRepository.existsByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(true);
        when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                snapshot,
                "gpt-4o-mini",
                "stock-explanation-v1"
        )).thenReturn(true);

        StockDetailResponse response = service.getStockDetailForCurrentUser("msft");

        assertEquals("MSFT", response.symbol());
        assertEquals("Microsoft", response.companyName());
        assertEquals(new BigDecimal("422.12"), response.currentPrice());
        assertEquals(new BigDecimal("0.50"), response.percentChange());
        assertEquals("stock_price_daily", response.dataSource());
        assertEquals("hash-MSFT-20", response.snapshotHash());
        assertTrue(response.isWatchlisted());
        assertTrue(response.aiExplanationAvailable());
        assertEquals("/api/stocks/MSFT/ai-explanation?timeframe=7D", response.aiExplanationEndpoint());
        assertTrue(response.tradeSupported());
    }

    @Test
    void detailFallsBackToSnapshotPriceFieldsWhenStoredStockRowIsMissing() {
        StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
        when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.empty());
        when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT", "7D"))
                .thenReturn(Optional.of(snapshot));

        StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

        assertNull(response.stockId());
        assertEquals("Microsoft", response.companyName());
        assertEquals(new BigDecimal("123.45"), response.currentPrice());
        assertEquals(new BigDecimal("2.34"), response.percentChange());
        assertEquals(20L, response.latestAnalysisSnapshotId());
    }

    @Test
    void detailUsesDeterministicLatestSnapshotLookup() {
        StockAnalysisSnapshot selectedSnapshot = snapshot("MSFT", 21L, "moderate", "newest tie breaker");
        selectedSnapshot.setCreatedAt(LocalDateTime.of(2026, 1, 5, 12, 0));
        when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
        when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT", "7D"))
                .thenReturn(Optional.of(selectedSnapshot));

        StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

        assertEquals(21L, response.latestAnalysisSnapshotId());
        assertEquals("hash-MSFT-21", response.snapshotHash());
        verify(snapshotRepository).findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT", "7D");
        verify(snapshotRepository, never()).findTopBySymbolAndTimeframeOrderByCreatedAtDesc("MSFT", "7D");
    }

    @Test
    void detailReportsAiExplanationUnavailableWhenOnlyOldSnapshotExplanationExists() {
        StockAnalysisSnapshot latestSnapshot = snapshot("MSFT", 21L, "moderate", "latest");
        when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
        when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT", "7D"))
                .thenReturn(Optional.of(latestSnapshot));
        when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                latestSnapshot,
                "gpt-4o-mini",
                "stock-explanation-v1"
        )).thenReturn(false);

        StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

        assertFalse(response.aiExplanationAvailable());
    }

    @Test
    void detailReportsAiExplanationAvailableForLatestSnapshotModelAndPromptVersion() {
        StockAnalysisSnapshot latestSnapshot = snapshot("MSFT", 21L, "moderate", "latest");
        when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
        when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT", "7D"))
                .thenReturn(Optional.of(latestSnapshot));
        when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                latestSnapshot,
                "gpt-4o-mini",
                "stock-explanation-v1"
        )).thenReturn(true);

        StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

        assertTrue(response.aiExplanationAvailable());
    }

    @Test
    void detailRejectsUnsupportedSymbol() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getStockDetailForCurrentUser("META")
        );

        assertEquals("Unsupported stock symbol: META", exception.getMessage());
        verify(stockRepository, never()).findBySymbol(anyString());
    }

    @Test
    void historyOneDayReturnsStoredIntradayPointsForLatestTradingDate() {
        LocalDate tradingDate = LocalDate.of(2026, 1, 5);
        when(historyRepository.findLatestTradingDateBySymbol("MSFT")).thenReturn(Optional.of(tradingDate));
        when(historyRepository.findBySymbolAndTradingDateOrderByTimestampAsc("MSFT", tradingDate))
                .thenReturn(List.of(intraday("MSFT", tradingDate, 9, 30), intraday("MSFT", tradingDate, 9, 31)));

        StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1D");

        assertEquals("MSFT", response.symbol());
        assertEquals("1D", response.timeframe());
        assertEquals("stock_price_history_1min", response.source());
        assertEquals(2, response.points().size());
        assertEquals(tradingDate, response.points().get(0).tradingDate());
        assertEquals(LocalDateTime.of(2026, 1, 5, 9, 30), response.points().get(0).timestamp());
    }

    @Test
    void historyOneDayReturnsEmptyWhenNoNonNullIntradayTradingDateExists() {
        when(historyRepository.findLatestTradingDateBySymbol("MSFT")).thenReturn(Optional.empty());

        StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1D");

        assertEquals("MSFT", response.symbol());
        assertTrue(response.points().isEmpty());
        assertEquals("No stored intraday history is available for MSFT", response.message());
        verify(historyRepository, never()).findBySymbolAndTradingDateOrderByTimestampAsc(anyString(), any(LocalDate.class));
    }

    @Test
    void historySevenDayReturnsLatestStoredDailyPointsOldestFirst() {
        LocalDate day1 = LocalDate.of(2026, 1, 1);
        LocalDate day2 = LocalDate.of(2026, 1, 2);
        LocalDate day3 = LocalDate.of(2026, 1, 5);
        when(dailyRepository.findBySymbolOrderByTradingDateDesc(eq("MSFT"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(daily("MSFT", day3), daily("MSFT", day2), daily("MSFT", day1)));

        StockHistoryResponse response = service.getStockHistoryForCurrentUser("msft", "7d");

        assertEquals("7D", response.timeframe());
        assertEquals("stock_price_daily", response.source());
        assertEquals(List.of(day1, day2, day3), response.points().stream().map(point -> point.tradingDate()).toList());
        assertNull(response.points().get(0).timestamp());
    }

    @Test
    void historyRejectsUnsupportedTimeframe() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getStockHistoryForCurrentUser("MSFT", "30D")
        );

        assertEquals("Unsupported stock history timeframe: 30D", exception.getMessage());
    }

    @Test
    void serviceDoesNotInjectMutatingOrGenerativeServices() {
        Set<String> fieldTypeNames = List.of(StockMarketDataServiceImpl.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.market.stock.service.StockService"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.market.stock.service.StockApiClient"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.ai.service.OpenAiClient"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.analysis.service.StockAnalysisService"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.ai.service.StockAiExplanationService"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.papertrading.service.PaperTradingService"));
    }

    @Test
    void listReadsOnlySupportedSymbolsFromRepository() {
        service.getStocksForCurrentUser();

        verify(stockRepository).findBySymbolIn(org.mockito.ArgumentMatchers.argThat((Collection<String> symbols) ->
                symbols.containsAll(List.of("NVDA", "TSLA", "AMD", "AAPL", "MSFT", "GOOG", "KO", "JNJ"))
                        && symbols.size() == 8
        ));
    }

    private Stock stock(String symbol, String companyName, String price) {
        Stock stock = new Stock();
        stock.setStockId(1L);
        stock.setSymbol(symbol);
        stock.setCompanyName(companyName);
        stock.setCurrentPrice(new BigDecimal(price));
        stock.setPercentChange(new BigDecimal("0.50"));
        stock.setDayHigh(new BigDecimal("130.00"));
        stock.setDayLow(new BigDecimal("120.00"));
        stock.setLastUpdated(LocalDateTime.of(2026, 1, 5, 16, 0));
        stock.setIsMarketOpen(false);
        stock.setTimezone("America/New_York");
        stock.setSource("TwelveData");
        return stock;
    }

    private StockAnalysisSnapshot snapshot(String symbol, Long snapshotId, String riskCategory, String trend) {
        StockAnalysisSnapshot snapshot = new StockAnalysisSnapshot();
        snapshot.setAnalysisSnapshotId(snapshotId);
        snapshot.setSymbol(symbol);
        snapshot.setTimeframe("7D");
        snapshot.setCurrentPrice(new BigDecimal("123.45"));
        snapshot.setPercentChange(new BigDecimal("2.34"));
        snapshot.setHighPrice(new BigDecimal("130.00"));
        snapshot.setLowPrice(new BigDecimal("118.00"));
        snapshot.setTrend(trend);
        snapshot.setVolatilityLabel("low");
        snapshot.setVolumeTrend("stable");
        snapshot.setPriceConsistency("smooth upward movement");
        snapshot.setRiskCategory(riskCategory);
        snapshot.setBaselineRiskCategory("moderate");
        snapshot.setDataSource("stock_price_daily");
        snapshot.setIsFallback(false);
        snapshot.setMissingDataCount(0);
        snapshot.setSnapshotHash("hash-" + symbol + "-" + snapshotId);
        snapshot.setCreatedAt(LocalDateTime.of(2026, 1, 5, 12, 0));
        return snapshot;
    }

    private StockPriceHistory intraday(String symbol, LocalDate tradingDate, int hour, int minute) {
        StockPriceHistory history = new StockPriceHistory();
        history.setSymbol(symbol);
        history.setTimestamp(tradingDate.atTime(hour, minute));
        history.setTradingDate(tradingDate);
        history.setOpenPrice(new BigDecimal("100.00"));
        history.setHighPrice(new BigDecimal("101.00"));
        history.setLowPrice(new BigDecimal("99.00"));
        history.setClosePrice(new BigDecimal("100.50"));
        history.setVolume(1000L);
        history.setSource("TwelveData");
        return history;
    }

    private StockPriceDaily daily(String symbol, LocalDate tradingDate) {
        StockPriceDaily daily = new StockPriceDaily();
        daily.setSymbol(symbol);
        daily.setTradingDate(tradingDate);
        daily.setOpenPrice(new BigDecimal("100.00"));
        daily.setHighPrice(new BigDecimal("105.00"));
        daily.setLowPrice(new BigDecimal("98.00"));
        daily.setClosePrice(new BigDecimal("102.00"));
        daily.setVolume(5000L);
        daily.setSource("TwelveData");
        return daily;
    }
}
