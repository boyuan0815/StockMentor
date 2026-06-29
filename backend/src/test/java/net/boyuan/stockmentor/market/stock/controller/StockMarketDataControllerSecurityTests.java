package net.boyuan.stockmentor.market.stock.controller;

import net.boyuan.stockmentor.ai.entity.StockAiExplanation;
import net.boyuan.stockmentor.ai.repository.StockAiExplanationRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.ai.service.StockAiExplanationService;
import net.boyuan.stockmentor.analysis.dto.StockExplanationResponse;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedIntradayHistorySelection;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockMarketDataControllerSecurityTests {
        private static final String ROUTE_SYMBOL = "GOOG";

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private AppUserRepository appUserRepository;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private StockRepository stockRepository;
        @Autowired
        private StockAnalysisSnapshotRepository snapshotRepository;
        @Autowired
        private StockPriceHistoryRepository historyRepository;
        @Autowired
        private StockPriceDailyRepository dailyRepository;
        @Autowired
        private UserWatchlistRepository watchlistRepository;
        @Autowired
        private StockAiExplanationRepository explanationRepository;
        @Autowired
        private StockAiSuggestionBatchRepository suggestionBatchRepository;
        @Autowired
        private StockAiSuggestionItemRepository suggestionItemRepository;
        @Autowired
        private UserBehaviorProfileRepository behaviorProfileRepository;
        @Autowired
        private PaperTradeTransactionRepository paperTradeTransactionRepository;

        @MockitoBean
        private StockAiExplanationService stockAiExplanationService;
        @MockitoBean
        private DelayedMarketPriceService delayedMarketPriceService;

        private AppUser authUser;

        @BeforeEach
        void setUp() {
                authUser = ensureUser("stock-market-auth@example.com", "stock-market-auth");
                ensureStock(ROUTE_SYMBOL);
                ensureSnapshot(ROUTE_SYMBOL);
                ensureExplanation(ROUTE_SYMBOL);
                ensureWatchlist(ROUTE_SYMBOL);
                ensureIntraday(ROUTE_SYMBOL);
                ensureDaily(ROUTE_SYMBOL);

                when(delayedMarketPriceService.resolveForDisplay(anyString()))
                                .thenAnswer(invocation -> delayedPrice(invocation.getArgument(0)));
                when(delayedMarketPriceService.loadOneDayHistoryForDisplay(eq(ROUTE_SYMBOL)))
                                .thenAnswer(invocation -> new DelayedIntradayHistorySelection(
                                                historyRepository.findBySymbolAndTradingDateOrderByTimestampAsc(
                                                                ROUTE_SYMBOL,
                                                                LocalDate.of(2026, 1, 7)),
                                                delayedPrice(ROUTE_SYMBOL)));
                when(stockAiExplanationService.getOrGenerateExplanation(eq(ROUTE_SYMBOL), eq("7D")))
                                .thenReturn(new StockExplanationResponse(
                                                ROUTE_SYMBOL,
                                                "7D",
                                                "Cached beginner-friendly explanation.",
                                                List.of(),
                                                true,
                                                true,
                                                9001L,
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 1, 7),
                                                "stock_price_daily",
                                                false,
                                                "moderate",
                                                "moderate",
                                                "Returned cached AI explanation"));
        }

        @Test
        void stocksEndpointRequiresAuthentication() throws Exception {
                mockMvc.perform(get("/api/stocks"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1M"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void authenticatedUserCanReadStockListDetailAndHistory() throws Exception {
                mockMvc.perform(get("/api/stocks")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.userId").value(authUser.getUserId()))
                                .andExpect(jsonPath("$.stocks.length()").value(8))
                                .andExpect(jsonPath("$.stocks[0].symbol").value("NVDA"))
                                .andExpect(jsonPath("$.stocks[1].symbol").value("TSLA"))
                                .andExpect(jsonPath("$.stocks[5].symbol").value("GOOG"))
                                .andExpect(jsonPath("$.stocks[5].currentPrice").value(422.120000))
                                .andExpect(jsonPath("$.stocks[5].percentChange").value(1.2500))
                                .andExpect(jsonPath("$.stocks[5].displayedPrice").value(422.000000))
                                .andExpect(jsonPath("$.stocks[5].priceFreshnessStatus").value("DELAYED_15_MINUTES"))
                                .andExpect(jsonPath("$.stocks[5].isPriceAvailable").value(true))
                                .andExpect(jsonPath("$.stocks[5].isTradeExecutable").value(true))
                                .andExpect(jsonPath("$.stocks[5].marketTimeZone").value("America/New_York"))
                                .andExpect(jsonPath("$.stocks[5].riskCategory").value("moderate"))
                                .andExpect(jsonPath("$.stocks[5].isWatchlisted").value(true));

                mockMvc.perform(get("/api/stocks/goog")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.companyName").value("Google"))
                                .andExpect(jsonPath("$.currentPrice").value(422.120000))
                                .andExpect(jsonPath("$.percentChange").value(1.2500))
                                .andExpect(jsonPath("$.displayedPrice").value(422.000000))
                                .andExpect(jsonPath("$.priceSource").value("stock_price_history_1min"))
                                .andExpect(jsonPath("$.highPrice").value(423.000000))
                                .andExpect(jsonPath("$.lowPrice").value(419.000000))
                                .andExpect(jsonPath("$.analysisDataSource").value("stock_price_daily"))
                                .andExpect(jsonPath("$.snapshotHighPrice").value(430.000000))
                                .andExpect(jsonPath("$.snapshotLowPrice").value(410.000000))
                                .andExpect(jsonPath("$.snapshotTimeframe").value("7D"))
                                .andExpect(jsonPath("$.tradeSupported").value(true))
                                .andExpect(jsonPath("$.aiExplanationAvailable").value(true))
                                .andExpect(jsonPath("$.aiExplanationEndpoint")
                                                .value("/api/stocks/GOOG/ai-explanation?timeframe=7D"));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.timeframe").value("1D"))
                                .andExpect(jsonPath("$.source").value("stock_price_history_1min"))
                                .andExpect(jsonPath("$.points.length()").value(2))
                                .andExpect(jsonPath("$.targetDisplayMarketTime").value("2026-01-07T09:31:00"))
                                .andExpect(jsonPath("$.priceFreshnessStatus").value("DELAYED_15_MINUTES"));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=7D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.timeframe").value("7D"))
                                .andExpect(jsonPath("$.source").value("stock_price_daily"))
                                .andExpect(jsonPath("$.points.length()").value(7));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1M")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.timeframe").value("1M"))
                                .andExpect(jsonPath("$.source").value("stock_price_daily"))
                                .andExpect(jsonPath("$.points.length()").value(2));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=3M")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.timeframe").value("3M"))
                                .andExpect(jsonPath("$.source").value("stock_price_daily"))
                                .andExpect(jsonPath("$.points.length()").value(3));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=YTD")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.timeframe").value("YTD"))
                                .andExpect(jsonPath("$.source").value("stock_price_daily"))
                                .andExpect(jsonPath("$.points.length()").value(7));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1Y")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.timeframe").value("1Y"))
                                .andExpect(jsonPath("$.source").value("stock_price_daily"))
                                .andExpect(jsonPath("$.points.length()").value(8));
        }

        @Test
        void unsupportedSymbolAndTimeframeReturnBadRequest() throws Exception {
                mockMvc.perform(get("/api/stocks/META")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Unsupported stock symbol: META"));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=30D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Unsupported stock history timeframe: 30D"));
        }

        @Test
        void stockDetailHistoryAndAiExplanationRoutesCoexist() throws Exception {
                mockMvc.perform(get("/api/stocks/GOOG")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"));

                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.timeframe").value("1D"));

                mockMvc.perform(get("/api/stocks/GOOG/ai-explanation?timeframe=7D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("GOOG"))
                                .andExpect(jsonPath("$.available").value(true))
                                .andExpect(jsonPath("$.cached").value(true));
        }

        @Test
        void us009EndpointsDoNotMutateKeyTables() throws Exception {
                long stockCount = stockRepository.count();
                long snapshotCount = snapshotRepository.count();
                long historyCount = historyRepository.count();
                long dailyCount = dailyRepository.count();
                long explanationCount = explanationRepository.count();
                long suggestionBatchCount = suggestionBatchRepository.count();
                long suggestionItemCount = suggestionItemRepository.count();
                long behaviorProfileCount = behaviorProfileRepository.count();
                long paperTradeCount = paperTradeTransactionRepository.count();

                mockMvc.perform(get("/api/stocks")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=7D")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1M")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=3M")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=YTD")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/stocks/GOOG/history?timeframe=1Y")
                                .with(httpBasic("stock-market-auth@example.com", "password")))
                                .andExpect(status().isOk());

                assertEquals(stockCount, stockRepository.count());
                assertEquals(snapshotCount, snapshotRepository.count());
                assertEquals(historyCount, historyRepository.count());
                assertEquals(dailyCount, dailyRepository.count());
                assertEquals(explanationCount, explanationRepository.count());
                assertEquals(suggestionBatchCount, suggestionBatchRepository.count());
                assertEquals(suggestionItemCount, suggestionItemRepository.count());
                assertEquals(behaviorProfileCount, behaviorProfileRepository.count());
                assertEquals(paperTradeCount, paperTradeTransactionRepository.count());
        }

        private AppUser ensureUser(String email, String username) {
                return appUserRepository.findByEmailOrUsername(email, username)
                                .orElseGet(() -> {
                                        LocalDateTime now = LocalDateTime.now();
                                        AppUser user = new AppUser();
                                        user.setEmail(email);
                                        user.setUsername(username);
                                        user.setPasswordHash(passwordEncoder.encode("password"));
                                        user.setRole(AppUserRole.BEGINNER_INVESTOR);
                                        user.setStatus(AppUserStatus.ACTIVE);
                                        user.setIsDeleted(false);
                                        user.setOnboardingCompleted(true);
                                        user.setCreatedAt(now);
                                        user.setUpdatedAt(now);
                                        return appUserRepository.save(user);
                                });
        }

        private Stock ensureStock(String symbol) {
                Stock stock = stockRepository.findBySymbol(symbol).orElseGet(Stock::new);
                stock.setSymbol(symbol);
                stock.setCompanyName("Google");
                stock.setCurrentPrice(new BigDecimal("422.120000"));
                stock.setPercentChange(new BigDecimal("1.2500"));
                stock.setDayHigh(new BigDecimal("430.000000"));
                stock.setDayLow(new BigDecimal("410.000000"));
                stock.setLastUpdated(LocalDateTime.of(2026, 1, 7, 16, 0));
                stock.setIsMarketOpen(false);
                stock.setTimezone("America/New_York");
                stock.setSource("TwelveData");
                stock.setCreatedAt(LocalDateTime.now());
                stock.setUpdatedAt(LocalDateTime.now());
                return stockRepository.save(stock);
        }

        private StockAnalysisSnapshot ensureSnapshot(String symbol) {
                StockAnalysisSnapshot snapshot = new StockAnalysisSnapshot();
                snapshot.setSymbol(symbol);
                snapshot.setTimeframe("7D");
                snapshot.setDataStartDate(LocalDate.of(2026, 1, 1));
                snapshot.setDataEndDate(LocalDate.of(2026, 1, 7));
                snapshot.setCurrentPrice(new BigDecimal("123.450000"));
                snapshot.setPercentChange(new BigDecimal("2.3400"));
                snapshot.setHighPrice(new BigDecimal("430.000000"));
                snapshot.setLowPrice(new BigDecimal("410.000000"));
                snapshot.setTrend("strong uptrend");
                snapshot.setVolatilityLabel("low");
                snapshot.setVolumeTrend("stable");
                snapshot.setPriceConsistency("smooth upward movement");
                snapshot.setRiskCategory("moderate");
                snapshot.setBaselineRiskCategory("moderate");
                snapshot.setDataSource("stock_price_daily");
                snapshot.setIsFallback(false);
                snapshot.setMissingDataCount(0);
                snapshot.setSnapshotHash("route-test-goog-snapshot-" + System.nanoTime());
                snapshot.setCreatedAt(LocalDateTime.now());
                return snapshotRepository.save(snapshot);
        }

        private void ensureExplanation(String symbol) {
                StockAnalysisSnapshot snapshot = snapshotRepository
                                .findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(symbol, "7D")
                                .orElseThrow();
                if (explanationRepository.existsByAnalysisSnapshotAndModelAndPromptVersion(
                                snapshot,
                                "gpt-4o-mini",
                                "stock-explanation-v1")) {
                        return;
                }
                StockAiExplanation explanation = new StockAiExplanation();
                explanation.setAnalysisSnapshot(snapshot);
                explanation.setSymbol(symbol);
                explanation.setTimeframe("7D");
                explanation.setModel("gpt-4o-mini");
                explanation.setPromptVersion("stock-explanation-v1");
                explanation.setExplanation("Stored explanation.");
                explanation.setCreatedAt(LocalDateTime.now());
                explanationRepository.save(explanation);
        }

        private void ensureWatchlist(String symbol) {
                if (watchlistRepository.findByUserUserIdAndSymbol(authUser.getUserId(), symbol).isPresent()) {
                        return;
                }
                UserWatchlist watchlist = new UserWatchlist();
                watchlist.setUser(authUser);
                watchlist.setSymbol(symbol);
                watchlist.setSource(WatchlistSource.MANUAL);
                watchlist.setCreatedAt(LocalDateTime.now());
                watchlist.setUpdatedAt(LocalDateTime.now());
                watchlistRepository.save(watchlist);
        }

        private void ensureIntraday(String symbol) {
                LocalDate date = LocalDate.of(2026, 1, 7);
                if (!historyRepository.findBySymbolAndTradingDateOrderByTimestampAsc(symbol, date).isEmpty()) {
                        return;
                }
                Stock stock = stockRepository.findBySymbol(symbol).orElseThrow();
                historyRepository.saveAll(List.of(
                                intraday(stock, symbol, date, 9, 30),
                                intraday(stock, symbol, date, 9, 31)));
        }

        private StockPriceHistory intraday(Stock stock, String symbol, LocalDate date, int hour, int minute) {
                StockPriceHistory history = new StockPriceHistory();
                history.setStock(stock);
                history.setSymbol(symbol);
                history.setTimestamp(date.atTime(hour, minute));
                history.setTradingDate(date);
                history.setOpenPrice(new BigDecimal("420.000000"));
                history.setHighPrice(new BigDecimal("423.000000"));
                history.setLowPrice(new BigDecimal("419.000000"));
                history.setClosePrice(new BigDecimal("422.000000"));
                history.setVolume(1000L);
                history.setTimeInterval("1min");
                history.setSource("TwelveData");
                history.setCreatedAt(LocalDateTime.now());
                return history;
        }

        private void ensureDaily(String symbol) {
                Stock stock = stockRepository.findBySymbol(symbol).orElseThrow();
                for (LocalDate date : List.of(
                                LocalDate.of(2025, 6, 12),
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 5),
                                LocalDate.of(2026, 1, 6),
                                LocalDate.of(2026, 1, 7),
                                LocalDate.of(2026, 3, 12),
                                LocalDate.of(2026, 5, 12),
                                LocalDate.of(2026, 6, 12))) {
                        StockPriceDaily daily = dailyRepository.findBySymbolAndTradingDate(symbol, date)
                                        .orElseGet(StockPriceDaily::new);
                        daily.setStock(stock);
                        daily.setSymbol(symbol);
                        daily.setTradingDate(date);
                        daily.setOpenPrice(new BigDecimal("420.000000"));
                        daily.setHighPrice(new BigDecimal("430.000000"));
                        daily.setLowPrice(new BigDecimal("410.000000"));
                        daily.setClosePrice(new BigDecimal("422.000000"));
                        daily.setVolume(5000L);
                        daily.setSource("TwelveData");
                        daily.setCreatedAt(LocalDateTime.now());
                        daily.setUpdatedAt(LocalDateTime.now());
                        dailyRepository.save(daily);
                }
        }

        private DelayedMarketPrice delayedPrice(String symbol) {
                return new DelayedMarketPrice(
                                symbol,
                                new BigDecimal("422.000000"),
                                new BigDecimal("0.4762"),
                                LocalDateTime.of(2026, 1, 7, 9, 31),
                                LocalDateTime.of(2026, 1, 7, 9, 31),
                                15,
                                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                                true,
                                true,
                                "Prices shown are delayed by about 15 minutes.",
                                DelayedMarketPriceService.INTRADAY_PRICE_SOURCE,
                                "America/New_York",
                                LocalDateTime.of(2026, 1, 7, 10, 0),
                                LocalDate.of(2026, 1, 7));
        }
}
