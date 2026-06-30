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
import net.boyuan.stockmentor.market.stock.model.DelayedIntradayHistorySelection;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.IntradayDayRangeProjection;
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
        @Mock
        private DelayedMarketPriceService delayedMarketPriceService;

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
                                explanationRepository,
                                delayedMarketPriceService);

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
                                anyString())).thenReturn(List.of());
                lenient().when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
                                anyString(),
                                anyString())).thenReturn(Optional.empty());
                lenient().when(watchlistRepository.findByUserUserIdAndSymbolIn(eq(1L), anyCollection()))
                                .thenReturn(List.of());
                lenient().when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                                any(StockAnalysisSnapshot.class),
                                anyString(),
                                anyString())).thenReturn(false);
                lenient().when(delayedMarketPriceService.resolveForDisplay(anyString()))
                                .thenAnswer(invocation -> delayedPrice(invocation.getArgument(0)));
                lenient().when(delayedMarketPriceService.loadOneDayHistoryForDisplay(anyString()))
                                .thenAnswer(invocation -> new DelayedIntradayHistorySelection(
                                                List.of(),
                                                delayedPrice(invocation.getArgument(0))));
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
                assertEquals(new BigDecimal("100.50"), item.displayedPrice());
                assertEquals("DELAYED_15_MINUTES", item.priceFreshnessStatus());
                assertTrue(item.isPriceAvailable());
                assertTrue(item.isTradeExecutable());
                assertEquals("America/New_York", item.marketTimeZone());
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
                                eq("7D"))).thenReturn(List.of(snapshot));

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
                                eq("7D"))).thenReturn(List.of(newerSnapshot, olderSnapshot));

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
                when(watchlistRepository.findByUserUserIdAndSymbolIn(eq(1L), anyCollection()))
                                .thenReturn(List.of(watchlist));

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
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(snapshot));
                when(watchlistRepository.existsByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(true);
                when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                                snapshot,
                                "gpt-4o-mini",
                                "stock-explanation-v3")).thenReturn(true);

                StockDetailResponse response = service.getStockDetailForCurrentUser("msft");

                assertEquals("MSFT", response.symbol());
                assertEquals("Microsoft", response.companyName());
                assertEquals(new BigDecimal("422.12"), response.currentPrice());
                assertEquals(new BigDecimal("0.50"), response.percentChange());
                assertEquals("stock_price_daily", response.dataSource());
                assertEquals("stock_price_daily", response.analysisDataSource());
                assertEquals(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE, response.priceSource());
                assertEquals("hash-MSFT-20", response.snapshotHash());
                assertTrue(response.isWatchlisted());
                assertTrue(response.aiExplanationAvailable());
                assertEquals("/api/stocks/MSFT/ai-explanation?timeframe=7D", response.aiExplanationEndpoint());
                assertTrue(response.tradeSupported());
        }

        @Test
        void detailHighLowUseDisplayedIntradayDayRangeAndSnapshotFieldsUseAnalysisRange() {
                Stock msft = stock("MSFT", "Microsoft", "422.12");
                StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
                when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.of(msft));
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(snapshot));
                when(historyRepository.findDayRangeAtOrBefore(
                                "MSFT",
                                LocalDate.of(2026, 1, 5),
                                "1min",
                                LocalDateTime.of(2026, 1, 5, 9, 45))).thenReturn(intradayRange("104.00", "97.00"));
                when(historyRepository.sumVolumeAtOrBefore(
                                "MSFT",
                                LocalDate.of(2026, 1, 5),
                                "1min",
                                LocalDateTime.of(2026, 1, 5, 9, 45))).thenReturn(3200L);

                StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

                assertEquals(new BigDecimal("104.00"), response.highPrice());
                assertEquals(new BigDecimal("97.00"), response.lowPrice());
                assertEquals(new BigDecimal("99.00"), response.previousClose());
                assertEquals(new BigDecimal("1.50"), response.displayedAbsoluteChange());
                assertEquals(3200L, response.displayedVolume());
                assertEquals(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE, response.priceSource());
                assertEquals("stock_price_daily", response.analysisDataSource());
                assertEquals(new BigDecimal("130.00"), response.snapshotHighPrice());
                assertEquals(new BigDecimal("118.00"), response.snapshotLowPrice());
                assertEquals("7D", response.snapshotTimeframe());
        }

        @Test
        void detailClosedDailyDayRangeUsesSelectedDailyCandle() {
                LocalDate tradingDate = LocalDate.of(2026, 1, 5);
                StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
                StockPriceDaily daily = daily("MSFT", tradingDate);
                when(stockRepository.findBySymbol("MSFT"))
                                .thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(snapshot));
                when(delayedMarketPriceService.resolveForDisplay("MSFT"))
                                .thenReturn(dailyDelayedPrice("MSFT", tradingDate));
                when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate)).thenReturn(Optional.of(daily));

                StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

                assertEquals(new BigDecimal("105.00"), response.highPrice());
                assertEquals(new BigDecimal("98.00"), response.lowPrice());
                assertEquals(new BigDecimal("100.00"), response.previousClose());
                assertEquals(new BigDecimal("2.00"), response.displayedAbsoluteChange());
                assertEquals(5000L, response.displayedVolume());
                verify(historyRepository, never()).findDayRangeAtOrBefore(
                                anyString(),
                                any(LocalDate.class),
                                anyString(),
                                any(LocalDateTime.class));
        }

        @Test
        void detailHighLowAreNullWhenDisplayedDayRangeIsUnavailable() {
                StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
                when(stockRepository.findBySymbol("MSFT"))
                                .thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(snapshot));

                StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

                assertNull(response.highPrice());
                assertNull(response.lowPrice());
                assertEquals(new BigDecimal("130.00"), response.snapshotHighPrice());
                assertEquals(new BigDecimal("118.00"), response.snapshotLowPrice());
                assertEquals("7D", response.snapshotTimeframe());
        }

        @Test
        void detailFallsBackToSnapshotPriceFieldsWhenStoredStockRowIsMissing() {
                StockAnalysisSnapshot snapshot = snapshot("MSFT", 20L, "moderate", "strong uptrend");
                when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.empty());
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
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
                when(stockRepository.findBySymbol("MSFT"))
                                .thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(selectedSnapshot));

                StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

                assertEquals(21L, response.latestAnalysisSnapshotId());
                assertEquals("hash-MSFT-21", response.snapshotHash());
                verify(snapshotRepository).findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D");
                verify(snapshotRepository, never()).findTopBySymbolAndTimeframeOrderByCreatedAtDesc("MSFT", "7D");
        }

        @Test
        void detailReportsAiExplanationUnavailableWhenOnlyOldSnapshotExplanationExists() {
                StockAnalysisSnapshot latestSnapshot = snapshot("MSFT", 21L, "moderate", "latest");
                when(stockRepository.findBySymbol("MSFT"))
                                .thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(latestSnapshot));
                when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                                latestSnapshot,
                                "gpt-4o-mini",
                                "stock-explanation-v3")).thenReturn(false);

                StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

                assertFalse(response.aiExplanationAvailable());
        }

        @Test
        void detailReportsAiExplanationAvailableForLatestSnapshotModelAndPromptVersion() {
                StockAnalysisSnapshot latestSnapshot = snapshot("MSFT", 21L, "moderate", "latest");
                when(stockRepository.findBySymbol("MSFT"))
                                .thenReturn(Optional.of(stock("MSFT", "Microsoft", "422.12")));
                when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc("MSFT",
                                "7D"))
                                .thenReturn(Optional.of(latestSnapshot));
                when(explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                                latestSnapshot,
                                "gpt-4o-mini",
                                "stock-explanation-v3")).thenReturn(true);

                StockDetailResponse response = service.getStockDetailForCurrentUser("MSFT");

                assertTrue(response.aiExplanationAvailable());
        }

        @Test
        void detailRejectsUnsupportedSymbol() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> service.getStockDetailForCurrentUser("META"));

                assertEquals("Unsupported stock symbol: META", exception.getMessage());
                verify(stockRepository, never()).findBySymbol(anyString());
        }

        @Test
        void historyOneDayReturnsStoredIntradayPointsForLatestTradingDate() {
                LocalDate tradingDate = LocalDate.of(2026, 1, 5);
                when(delayedMarketPriceService.loadOneDayHistoryForDisplay("MSFT"))
                                .thenReturn(new DelayedIntradayHistorySelection(
                                                List.of(intraday("MSFT", tradingDate, 9, 30),
                                                                intraday("MSFT", tradingDate, 9, 31)),
                                                delayedPrice("MSFT")));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1D");

                assertEquals("MSFT", response.symbol());
                assertEquals("1D", response.timeframe());
                assertEquals("stock_price_history_1min", response.source());
                assertEquals(2, response.points().size());
                assertEquals(tradingDate, response.points().get(0).tradingDate());
                assertEquals(LocalDateTime.of(2026, 1, 5, 9, 30), response.points().get(0).timestamp());
                assertEquals("DELAYED_15_MINUTES", response.priceFreshnessStatus());
                assertEquals("America/New_York", response.marketTimeZone());
        }

        @Test
        void historyOneDayFiltersRegularSessionRowsAndUsesDelayedCutoffExpectedCount() {
                LocalDate tradingDate = LocalDate.of(2026, 1, 5);
                when(delayedMarketPriceService.loadOneDayHistoryForDisplay("MSFT"))
                                .thenReturn(new DelayedIntradayHistorySelection(
                                                List.of(
                                                                intraday("MSFT", tradingDate, 9, 29),
                                                                intraday("MSFT", tradingDate, 9, 30),
                                                                intraday("MSFT", tradingDate, 10, 15),
                                                                intraday("MSFT", tradingDate, 16, 0)),
                                                delayedPriceAt("MSFT", tradingDate, 10, 15)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1D");

                assertEquals(List.of(
                                LocalDateTime.of(2026, 1, 5, 9, 30),
                                LocalDateTime.of(2026, 1, 5, 10, 15)),
                                response.points().stream().map(point -> point.timestamp()).toList());
                assertEquals(46, response.expectedPointCount());
                assertEquals(2, response.actualPointCount());
                assertEquals(44, response.missingDataCount());
        }

        @Test
        void historyOneDayReturnsIntradayRowsEvenWhenQuoteMetadataUsesDailyFallback() {
                LocalDate tradingDate = LocalDate.of(2026, 1, 5);
                when(delayedMarketPriceService.loadOneDayHistoryForDisplay("MSFT"))
                                .thenReturn(new DelayedIntradayHistorySelection(
                                                List.of(intraday("MSFT", tradingDate, 9, 30),
                                                                intraday("MSFT", tradingDate, 15, 59)),
                                                preOpenDailyFallbackPrice("MSFT", tradingDate)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1D");

                assertEquals("stock_price_history_1min", response.source());
                assertEquals(2, response.points().size());
                assertEquals(DelayedMarketPriceService.DAILY_PRICE_SOURCE, response.priceSource());
                assertEquals("MARKET_CLOSED_LAST_CLOSE", response.priceFreshnessStatus());
                assertEquals(LocalDateTime.of(2026, 1, 5, 16, 0), response.targetDisplayMarketTime());
        }

        @Test
        void historyOneDayReturnsEmptyWhenNoNonNullIntradayTradingDateExists() {
                when(delayedMarketPriceService.loadOneDayHistoryForDisplay("MSFT"))
                                .thenReturn(new DelayedIntradayHistorySelection(List.of(), delayedPrice("MSFT")));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1D");

                assertEquals("MSFT", response.symbol());
                assertTrue(response.points().isEmpty());
                assertEquals("No stored delayed intraday history is available for MSFT", response.message());
        }

        @Test
        void historyFiveDayReturnsStoredIntradayRowsWithCompletenessMetadata() {
                LocalDate day1 = LocalDate.of(2026, 1, 2);
                LocalDate day2 = LocalDate.of(2026, 1, 5);
                when(delayedMarketPriceService.resolveForDisplay("MSFT"))
                                .thenReturn(delayedPriceAt("MSFT", day2, 16, 0));
                when(historyRepository.findLatestTradingDates(eq("MSFT"), eq("1min"),
                                org.mockito.ArgumentMatchers.any(Pageable.class)))
                                .thenReturn(List.of(day2, day1));
                when(historyRepository.findBySymbolAndTradingDateInAndTimeIntervalOrderByTimestampAsc(
                                "MSFT",
                                List.of(day1, day2),
                                "1min")).thenReturn(List.of(
                                                intraday("MSFT", day1, 9, 30),
                                                intraday("MSFT", day2, 15, 59)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "5D");

                assertEquals("5D", response.timeframe());
                assertEquals("stock_price_history_1min", response.source());
                assertEquals(List.of(day1, day2),
                                response.points().stream().map(point -> point.tradingDate()).toList());
                assertEquals("INTRADAY_1MIN", response.granularity());
                assertTrue(response.lineChartSupported());
                assertTrue(response.candlestickSupported());
                assertEquals(1950, response.expectedPointCount());
                assertEquals(2, response.actualPointCount());
                assertEquals(1948, response.missingDataCount());
                assertEquals(2, response.includedTradingDays());
                assertEquals(5, response.requestedTradingDays());
                assertEquals("America/New_York", response.timezone());
                assertEquals(new BigDecimal("99.00"), response.previousClose());
                assertEquals(new BigDecimal("1.50"), response.displayedAbsoluteChange());
                assertEquals("Delayed 15 min", response.priceFreshnessLabel());
        }

        @Test
        void historyFiveDayFiltersRegularSessionRowsAndUsesPartialCurrentDayExpectedCount() {
                LocalDate previousDay = LocalDate.of(2026, 1, 2);
                LocalDate currentDay = LocalDate.of(2026, 1, 5);
                when(delayedMarketPriceService.resolveForDisplay("MSFT"))
                                .thenReturn(delayedPriceAt("MSFT", currentDay, 10, 15));
                when(historyRepository.findLatestTradingDates(eq("MSFT"), eq("1min"),
                                org.mockito.ArgumentMatchers.any(Pageable.class)))
                                .thenReturn(List.of(currentDay, previousDay));
                when(historyRepository.findBySymbolAndTradingDateInAndTimeIntervalOrderByTimestampAsc(
                                "MSFT",
                                List.of(previousDay, currentDay),
                                "1min")).thenReturn(List.of(
                                                intraday("MSFT", previousDay, 9, 30),
                                                intraday("MSFT", previousDay, 16, 0),
                                                intraday("MSFT", currentDay, 9, 29),
                                                intraday("MSFT", currentDay, 10, 15),
                                                intraday("MSFT", currentDay, 10, 16)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "5D");

                assertEquals(List.of(
                                LocalDateTime.of(2026, 1, 2, 9, 30),
                                LocalDateTime.of(2026, 1, 5, 10, 15)),
                                response.points().stream().map(point -> point.timestamp()).toList());
                assertEquals(1606, response.expectedPointCount());
                assertEquals(2, response.actualPointCount());
                assertEquals(1604, response.missingDataCount());
        }

        @Test
        void historySevenDayReturnsLatestStoredDailyPointsOldestFirst() {
                LocalDate day1 = LocalDate.of(2026, 1, 1);
                LocalDate day2 = LocalDate.of(2026, 1, 2);
                LocalDate day3 = LocalDate.of(2026, 1, 5);
                when(dailyRepository.findBySymbolOrderByTradingDateDesc(eq("MSFT"),
                                org.mockito.ArgumentMatchers.any(Pageable.class)))
                                .thenReturn(List.of(daily("MSFT", day3), daily("MSFT", day2), daily("MSFT", day1)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("msft", "7d");

                assertEquals("7D", response.timeframe());
                assertEquals("stock_price_daily", response.source());
                assertEquals(List.of(day1, day2, day3),
                                response.points().stream().map(point -> point.tradingDate()).toList());
                assertNull(response.points().get(0).timestamp());
        }

        @Test
        void historyOneMonthReturnsStoredDailyRangeFromLatestDateMinusOneMonth() {
                LocalDate latestDate = LocalDate.of(2026, 6, 12);
                LocalDate startDate = LocalDate.of(2026, 5, 12);
                when(dailyRepository.findTopBySymbolOrderByTradingDateDesc("MSFT"))
                                .thenReturn(Optional.of(daily("MSFT", latestDate)));
                when(dailyRepository.findBySymbolAndTradingDateBetweenOrderByTradingDateAsc("MSFT", startDate,
                                latestDate))
                                .thenReturn(List.of(daily("MSFT", startDate), daily("MSFT", LocalDate.of(2026, 5, 29)),
                                                daily("MSFT", latestDate)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1M");

                assertEquals("1M", response.timeframe());
                assertEquals("stock_price_daily", response.source());
                assertEquals(List.of(startDate, LocalDate.of(2026, 5, 29), latestDate),
                                response.points().stream().map(point -> point.tradingDate()).toList());
                verify(historyRepository, never()).findLatestTradingDateBySymbol(anyString());
        }

        @Test
        void historyThreeMonthReturnsStoredDailyRangeFromLatestDateMinusThreeMonths() {
                LocalDate latestDate = LocalDate.of(2026, 6, 12);
                LocalDate startDate = LocalDate.of(2026, 3, 12);
                when(dailyRepository.findTopBySymbolOrderByTradingDateDesc("MSFT"))
                                .thenReturn(Optional.of(daily("MSFT", latestDate)));
                when(dailyRepository.findBySymbolAndTradingDateBetweenOrderByTradingDateAsc("MSFT", startDate,
                                latestDate))
                                .thenReturn(List.of(daily("MSFT", startDate), daily("MSFT", latestDate)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "3M");

                assertEquals("3M", response.timeframe());
                assertEquals(List.of(startDate, latestDate),
                                response.points().stream().map(point -> point.tradingDate()).toList());
        }

        @Test
        void historyYearToDateReturnsStoredDailyRangeFromJanuaryFirstOfLatestYear() {
                LocalDate latestDate = LocalDate.of(2026, 6, 12);
                LocalDate startDate = LocalDate.of(2026, 1, 1);
                when(dailyRepository.findTopBySymbolOrderByTradingDateDesc("MSFT"))
                                .thenReturn(Optional.of(daily("MSFT", latestDate)));
                when(dailyRepository.findBySymbolAndTradingDateBetweenOrderByTradingDateAsc("MSFT", startDate,
                                latestDate))
                                .thenReturn(List.of(daily("MSFT", startDate), daily("MSFT", LocalDate.of(2026, 4, 1)),
                                                daily("MSFT", latestDate)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "YTD");

                assertEquals("YTD", response.timeframe());
                assertEquals(List.of(startDate, LocalDate.of(2026, 4, 1), latestDate),
                                response.points().stream().map(point -> point.tradingDate()).toList());
        }

        @Test
        void historyOneYearReturnsStoredDailyRangeFromLatestDateMinusOneYear() {
                LocalDate latestDate = LocalDate.of(2026, 6, 12);
                LocalDate startDate = LocalDate.of(2025, 6, 12);
                when(dailyRepository.findTopBySymbolOrderByTradingDateDesc("MSFT"))
                                .thenReturn(Optional.of(daily("MSFT", latestDate)));
                when(dailyRepository.findBySymbolAndTradingDateBetweenOrderByTradingDateAsc("MSFT", startDate,
                                latestDate))
                                .thenReturn(List.of(daily("MSFT", startDate), daily("MSFT", latestDate)));

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1Y");

                assertEquals("1Y", response.timeframe());
                assertEquals(List.of(startDate, latestDate),
                                response.points().stream().map(point -> point.tradingDate()).toList());
        }

        @Test
        void historyNormalizesLowercaseDailyRangeTimeframes() {
                LocalDate latestDate = LocalDate.of(2026, 6, 12);
                lenient().when(dailyRepository.findTopBySymbolOrderByTradingDateDesc("MSFT"))
                                .thenReturn(Optional.of(daily("MSFT", latestDate)));
                when(dailyRepository.findBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                                eq("MSFT"),
                                any(LocalDate.class),
                                eq(latestDate))).thenReturn(List.of(daily("MSFT", latestDate)));

                assertEquals("1M", service.getStockHistoryForCurrentUser("msft", "1m").timeframe());
                assertEquals("3M", service.getStockHistoryForCurrentUser("msft", "3m").timeframe());
                assertEquals("YTD", service.getStockHistoryForCurrentUser("msft", "ytd").timeframe());
                assertEquals("1Y", service.getStockHistoryForCurrentUser("msft", "1y").timeframe());
        }

        @Test
        void historyDailyRangeReturnsEmptyWhenNoDailyDataExists() {
                when(dailyRepository.findTopBySymbolOrderByTradingDateDesc("MSFT")).thenReturn(Optional.empty());

                StockHistoryResponse response = service.getStockHistoryForCurrentUser("MSFT", "1M");

                assertEquals("MSFT", response.symbol());
                assertEquals("1M", response.timeframe());
                assertEquals("stock_price_daily", response.source());
                assertTrue(response.points().isEmpty());
                assertEquals("No stored daily history is available for MSFT", response.message());
                verify(dailyRepository, never()).findBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                                anyString(),
                                any(LocalDate.class),
                                any(LocalDate.class));
        }

        @Test
        void historyRejectsUnsupportedTimeframe() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> service.getStockHistoryForCurrentUser("MSFT", "30D"));

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
                assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.ai.service.StockAiSuggestionService"));
                assertFalse(fieldTypeNames
                                .contains("net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService"));
                assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.papertrading.service.PaperTradingService"));
                assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.scheduler.StockScheduler"));
        }

        @Test
        void listReadsOnlySupportedSymbolsFromRepository() {
                service.getStocksForCurrentUser();

                verify(stockRepository).findBySymbolIn(org.mockito.ArgumentMatchers
                                .argThat((Collection<String> symbols) -> symbols.containsAll(
                                                List.of("NVDA", "TSLA", "AMD", "AAPL", "MSFT", "GOOG", "KO", "JNJ"))
                                                && symbols.size() == 8));
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

        private DelayedMarketPrice delayedPrice(String symbol) {
                return new DelayedMarketPrice(
                                symbol,
                                new BigDecimal("100.50"),
                                new BigDecimal("0.5000"),
                                LocalDateTime.of(2026, 1, 5, 9, 45),
                                LocalDateTime.of(2026, 1, 5, 9, 45),
                                15,
                                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                                true,
                                true,
                                "Prices shown are delayed by about 15 minutes.",
                                DelayedMarketPriceService.INTRADAY_PRICE_SOURCE,
                                "America/New_York",
                                LocalDateTime.of(2026, 1, 5, 10, 0),
                                LocalDate.of(2026, 1, 5),
                                new BigDecimal("99.00"),
                                new BigDecimal("1.50"),
                                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES.label());
        }

        private DelayedMarketPrice delayedPriceAt(String symbol, LocalDate tradingDate, int hour, int minute) {
                return new DelayedMarketPrice(
                                symbol,
                                new BigDecimal("100.50"),
                                new BigDecimal("0.5000"),
                                tradingDate.atTime(hour, minute),
                                tradingDate.atTime(hour, minute),
                                15,
                                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                                true,
                                true,
                                "Prices shown are delayed by about 15 minutes.",
                                DelayedMarketPriceService.INTRADAY_PRICE_SOURCE,
                                "America/New_York",
                                tradingDate.atTime(hour, minute).plusMinutes(1),
                                tradingDate,
                                new BigDecimal("99.00"),
                                new BigDecimal("1.50"),
                                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES.label());
        }

        private DelayedMarketPrice dailyDelayedPrice(String symbol, LocalDate tradingDate) {
                return new DelayedMarketPrice(
                                symbol,
                                new BigDecimal("102.00"),
                                new BigDecimal("2.0000"),
                                tradingDate.atTime(16, 0),
                                tradingDate.atTime(16, 0),
                                15,
                                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE,
                                true,
                                true,
                                "Market is closed. This practice trade uses the latest stored daily close, not a live quote.",
                                DelayedMarketPriceService.DAILY_PRICE_SOURCE,
                                "America/New_York",
                                LocalDateTime.of(2026, 1, 5, 19, 0),
                                tradingDate,
                                new BigDecimal("100.00"),
                                new BigDecimal("2.00"),
                                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE.label());
        }

        private DelayedMarketPrice preOpenDailyFallbackPrice(String symbol, LocalDate tradingDate) {
                return new DelayedMarketPrice(
                                symbol,
                                new BigDecimal("102.00"),
                                new BigDecimal("2.0000"),
                                tradingDate.atTime(16, 0),
                                tradingDate.atTime(16, 0),
                                15,
                                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE,
                                true,
                                true,
                                "Today's delayed market display starts around 9:45 AM New York time. Showing the latest stored daily close.",
                                DelayedMarketPriceService.DAILY_PRICE_SOURCE,
                                "America/New_York",
                                LocalDateTime.of(2026, 1, 5, 19, 0),
                                tradingDate,
                                new BigDecimal("100.00"),
                                new BigDecimal("2.00"),
                                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE.label());
        }

        private IntradayDayRangeProjection intradayRange(String highPrice, String lowPrice) {
                return new IntradayDayRangeProjection() {
                        @Override
                        public BigDecimal getHighPrice() {
                                return new BigDecimal(highPrice);
                        }

                        @Override
                        public BigDecimal getLowPrice() {
                                return new BigDecimal(lowPrice);
                        }
                };
        }
}
