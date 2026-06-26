package net.boyuan.stockmentor.market.stock.service;

import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.model.DelayedIntradayHistorySelection;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DelayedMarketPriceServiceTests {
    @Mock
    private StockPriceHistoryRepository historyRepository;
    @Mock
    private StockPriceDailyRepository dailyRepository;

    @Test
    void activeWindowUsesExactDelayedIntradayCandle() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T14:00:05Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        StockPriceHistory selected = intraday("MSFT", tradingDate, 9, 45, "101.00");
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 9, 45), BigDecimal.ZERO
        )).thenReturn(Optional.of(selected));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc("MSFT", tradingDate, "1min"))
                .thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));
        when(dailyRepository.findTopBySymbolAndTradingDateBeforeOrderByTradingDateDesc("MSFT", tradingDate))
                .thenReturn(Optional.of(daily("MSFT", LocalDate.of(2026, 6, 12), "100.00")));

        DelayedMarketPrice price = service.resolveForDisplay("msft");

        assertThat(price.displayedPrice()).isEqualByComparingTo("101.00");
        assertThat(price.displayedPercentChange()).isEqualByComparingTo("1.0000");
        assertThat(price.displayedMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 45));
        assertThat(price.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 45));
        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.DELAYED_15_MINUTES);
        assertThat(price.priceSource()).isEqualTo(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE);
        assertThat(price.priceAvailable()).isTrue();
        assertThat(price.tradeExecutable()).isTrue();
    }

    @Test
    void activeWindowUsesPreviousIntradayCandleWithinStalenessThreshold() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T14:00:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                eq("MSFT"), eq(tradingDate), eq("1min"), eq(LocalDateTime.of(2026, 6, 15, 9, 45)), eq(BigDecimal.ZERO)
        )).thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 40, "100.50")));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc("MSFT", tradingDate, "1min"))
                .thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.DELAYED_15_MINUTES);
        assertThat(price.displayedMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 40));
        assertThat(price.tradeExecutable()).isTrue();
    }

    @Test
    void activeWindowPercentChangeUsesFirstIntradayOpenAndSelectedDelayedClose() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T14:00:05Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 9, 45), BigDecimal.ZERO
        )).thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 45, "106.00")));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc("MSFT", tradingDate, "1min"))
                .thenReturn(Optional.of(intradayWithOpen("MSFT", tradingDate, 9, 30, "100.00", "101.00")));
        when(dailyRepository.findTopBySymbolAndTradingDateBeforeOrderByTradingDateDesc("MSFT", tradingDate))
                .thenReturn(Optional.of(daily("MSFT", LocalDate.of(2026, 6, 12), "100.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.displayedPrice()).isEqualByComparingTo("106.00");
        assertThat(price.displayedPercentChange()).isEqualByComparingTo("6.0000");
    }

    @Test
    void sixteenFourteenStillUsesDelayedIntradayTargetingFifteenFiftyNine() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T20:14:59Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 15, 59), BigDecimal.ZERO
        )).thenReturn(Optional.of(intraday("MSFT", tradingDate, 15, 59, "104.50")));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc("MSFT", tradingDate, "1min"))
                .thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.DELAYED_15_MINUTES);
        assertThat(price.priceSource()).isEqualTo(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE);
        assertThat(price.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 15, 59));
        assertThat(price.displayedMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 15, 59));
        assertThat(price.tradeExecutable()).isTrue();
    }

    @Test
    void activeWindowDailyFallbackIsDisplayOnlyWhenIntradayIsMissing() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T14:00:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        LocalDate previousTradingDate = LocalDate.of(2026, 6, 12);
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                eq("MSFT"), eq(tradingDate), eq("1min"), any(LocalDateTime.class), eq(BigDecimal.ZERO)
        )).thenReturn(Optional.empty());
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", previousTradingDate))
                .thenReturn(Optional.of(daily("MSFT", previousTradingDate, "102.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.LATEST_STORED_PRICE);
        assertThat(price.priceAvailable()).isTrue();
        assertThat(price.tradeExecutable()).isFalse();
        assertThat(price.priceSource()).isEqualTo(DelayedMarketPriceService.DAILY_PRICE_SOURCE);
    }

    @Test
    void preDelayedOpenUsesLatestCompletedDailyCloseWithPreviousCloseTarget() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T05:55:00Z");
        LocalDate latestCompletedTradingDate = LocalDate.of(2026, 6, 12);
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", latestCompletedTradingDate))
                .thenReturn(Optional.of(daily("MSFT", latestCompletedTradingDate, "102.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE);
        assertThat(price.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 12, 16, 0));
        assertThat(price.displayedMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 12, 16, 0));
        assertThat(price.priceSource()).isEqualTo(DelayedMarketPriceService.DAILY_PRICE_SOURCE);
        assertThat(price.tradeExecutable()).isTrue();
    }

    @Test
    void activeWindowRejectsTooStaleIntradayAndNoDailyFallback() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T14:00:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        LocalDate previousTradingDate = LocalDate.of(2026, 6, 12);
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                eq("MSFT"), eq(tradingDate), eq("1min"), any(LocalDateTime.class), eq(BigDecimal.ZERO)
        )).thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", previousTradingDate)).thenReturn(Optional.empty());

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.UNAVAILABLE);
        assertThat(price.priceAvailable()).isFalse();
        assertThat(price.tradeExecutable()).isFalse();
    }

    @Test
    void sixteenFifteenUsesDailyCloseWhenAvailableEvenIfFinalIntradayDiffers() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T20:15:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate))
                .thenReturn(Optional.of(daily("MSFT", tradingDate, "105.00")));
        when(dailyRepository.findTopBySymbolAndTradingDateBeforeOrderByTradingDateDesc("MSFT", tradingDate))
                .thenReturn(Optional.of(daily("MSFT", LocalDate.of(2026, 6, 12), "100.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE);
        assertThat(price.priceSource()).isEqualTo(DelayedMarketPriceService.DAILY_PRICE_SOURCE);
        assertThat(price.displayedPrice()).isEqualByComparingTo("105.00");
        assertThat(price.displayedPercentChange()).isEqualByComparingTo("5.0000");
        assertThat(price.displayedMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 16, 0));
        assertThat(price.tradeExecutable()).isTrue();
        verify(historyRepository, never())
                .findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                        anyString(), any(LocalDate.class), anyString(), any(LocalDateTime.class), any(BigDecimal.class)
                );
    }

    @Test
    void sixteenFifteenUsesPendingLatestSameDayIntradayWhenDailyCloseIsMissing() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T20:15:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate)).thenReturn(Optional.empty());
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 16, 0), BigDecimal.ZERO
        )).thenReturn(Optional.of(intraday("MSFT", tradingDate, 15, 59, "104.50")));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc("MSFT", tradingDate, "1min"))
                .thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.LATEST_STORED_PRICE);
        assertThat(price.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 16, 0));
        assertThat(price.displayedMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 15, 59));
        assertThat(price.priceSource()).isEqualTo(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE);
        assertThat(price.tradeExecutable()).isTrue();
    }

    @Test
    void weekendRequiresLatestCompletedTradingDayDailyClose() {
        DelayedMarketPriceService service = serviceAt("2026-06-13T16:00:00Z");
        LocalDate latestCompletedTradingDate = LocalDate.of(2026, 6, 12);
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", latestCompletedTradingDate)).thenReturn(Optional.empty());

        DelayedMarketPrice price = service.resolveForDisplay("MSFT");

        assertThat(price.priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.UNAVAILABLE);
        assertThat(price.priceAvailable()).isFalse();
        assertThat(price.tradeExecutable()).isFalse();
        verify(historyRepository, never())
                .findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                        anyString(), any(LocalDate.class), anyString(), any(LocalDateTime.class), any(BigDecimal.class)
                );
    }

    @Test
    void oneDayHistoryUsesDelayedCutoffAndDoesNotExposeNewerIntradayRows() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T14:00:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        StockPriceHistory visible = intraday("MSFT", tradingDate, 9, 45, "101.00");
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                eq("MSFT"), eq(tradingDate), eq("1min"), eq(LocalDateTime.of(2026, 6, 15, 9, 45)), eq(BigDecimal.ZERO)
        )).thenReturn(Optional.of(visible));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc("MSFT", tradingDate, "1min"))
                .thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));
        when(historyRepository.findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 9, 45)
        )).thenReturn(List.of(visible));

        DelayedIntradayHistorySelection selection = service.loadOneDayHistoryForDisplay("MSFT");

        assertThat(selection.rows()).containsExactly(visible);
        assertThat(selection.metadata().targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 45));
        verify(historyRepository).findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 9, 45)
        );
    }

    @Test
    void oneDayHistoryPreDelayedOpenReturnsLatestCompletedTradingDayIntradayRows() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T10:00:00Z");
        LocalDate latestCompletedTradingDate = LocalDate.of(2026, 6, 12);
        StockPriceHistory first = intraday("MSFT", latestCompletedTradingDate, 9, 30, "100.00");
        StockPriceHistory last = intraday("MSFT", latestCompletedTradingDate, 15, 59, "102.00");
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", latestCompletedTradingDate))
                .thenReturn(Optional.of(daily("MSFT", latestCompletedTradingDate, "102.00")));
        when(historyRepository.findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", latestCompletedTradingDate, "1min", LocalDateTime.of(2026, 6, 12, 16, 0)
        )).thenReturn(List.of(first, last));

        DelayedIntradayHistorySelection selection = service.loadOneDayHistoryForDisplay("MSFT");

        assertThat(selection.rows()).containsExactly(first, last);
        assertThat(selection.metadata().priceFreshnessStatus())
                .isEqualTo(DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE);
        assertThat(selection.metadata().priceSource()).isEqualTo(DelayedMarketPriceService.DAILY_PRICE_SOURCE);
        assertThat(selection.metadata().targetDisplayMarketTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 12, 16, 0));
        verify(historyRepository).findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", latestCompletedTradingDate, "1min", LocalDateTime.of(2026, 6, 12, 16, 0)
        );
    }

    @Test
    void oneDayHistoryPreDelayedOpenReturnsEmptyWhenLatestCompletedIntradayRowsAreMissing() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T10:00:00Z");
        LocalDate latestCompletedTradingDate = LocalDate.of(2026, 6, 12);
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", latestCompletedTradingDate))
                .thenReturn(Optional.of(daily("MSFT", latestCompletedTradingDate, "102.00")));
        when(historyRepository.findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", latestCompletedTradingDate, "1min", LocalDateTime.of(2026, 6, 12, 16, 0)
        )).thenReturn(List.of());

        DelayedIntradayHistorySelection selection = service.loadOneDayHistoryForDisplay("MSFT");

        assertThat(selection.rows()).isEmpty();
        assertThat(selection.metadata().priceFreshnessStatus())
                .isEqualTo(DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE);
    }

    @Test
    void oneDayHistoryNonTradingDayReturnsLatestCompletedTradingDayIntradayRows() {
        DelayedMarketPriceService service = serviceAt("2026-06-13T16:00:00Z");
        LocalDate latestCompletedTradingDate = LocalDate.of(2026, 6, 12);
        StockPriceHistory visible = intraday("MSFT", latestCompletedTradingDate, 15, 59, "102.00");
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", latestCompletedTradingDate))
                .thenReturn(Optional.of(daily("MSFT", latestCompletedTradingDate, "102.00")));
        when(historyRepository.findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", latestCompletedTradingDate, "1min", LocalDateTime.of(2026, 6, 12, 16, 0)
        )).thenReturn(List.of(visible));

        DelayedIntradayHistorySelection selection = service.loadOneDayHistoryForDisplay("MSFT");

        assertThat(selection.rows()).containsExactly(visible);
        assertThat(selection.metadata().priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE);
        assertThat(selection.metadata().priceSource()).isEqualTo(DelayedMarketPriceService.DAILY_PRICE_SOURCE);
    }

    @Test
    void oneDayHistoryPostDelayedCloseReturnsCurrentTradingDayRowsUpToMarketClose() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T20:30:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        StockPriceHistory visible = intraday("MSFT", tradingDate, 15, 59, "104.50");
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate))
                .thenReturn(Optional.of(daily("MSFT", tradingDate, "105.00")));
        when(historyRepository.findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 16, 0)
        )).thenReturn(List.of(visible));

        DelayedIntradayHistorySelection selection = service.loadOneDayHistoryForDisplay("MSFT");

        assertThat(selection.rows()).containsExactly(visible);
        assertThat(selection.metadata().priceFreshnessStatus()).isEqualTo(DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE);
        assertThat(selection.metadata().priceSource()).isEqualTo(DelayedMarketPriceService.DAILY_PRICE_SOURCE);
        verify(historyRepository).findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT", tradingDate, "1min", LocalDateTime.of(2026, 6, 15, 16, 0)
        );
    }

    @Test
    void oneDayHistoryPostDelayedClosePendingDailyCloseReturnsCurrentTradingDayRows() {
        DelayedMarketPriceService service = serviceAt("2026-06-15T20:30:00Z");
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        StockPriceHistory visible = intraday("MSFT", tradingDate, 15, 59, "104.50");

        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate)).thenReturn(Optional.empty());
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                "MSFT",
                tradingDate,
                "1min",
                LocalDateTime.of(2026, 6, 15, 16, 0),
                BigDecimal.ZERO
        )).thenReturn(Optional.of(visible));
        when(historyRepository.findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc(
                "MSFT",
                tradingDate,
                "1min"
        )).thenReturn(Optional.of(intraday("MSFT", tradingDate, 9, 30, "100.00")));
        when(historyRepository.findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                "MSFT",
                tradingDate,
                "1min",
                LocalDateTime.of(2026, 6, 15, 16, 0)
        )).thenReturn(List.of(visible));

        DelayedIntradayHistorySelection selection = service.loadOneDayHistoryForDisplay("MSFT");

        assertThat(selection.rows()).containsExactly(visible);
        assertThat(selection.metadata().priceFreshnessStatus())
                .isEqualTo(DelayedPriceFreshnessStatus.LATEST_STORED_PRICE);
        assertThat(selection.metadata().priceSource())
                .isEqualTo(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE);
        assertThat(selection.metadata().targetDisplayMarketTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 15, 16, 0));
    }

    private DelayedMarketPriceService serviceAt(String instant) {
        MarketTimeService marketTimeService = new MarketTimeService(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        return new DelayedMarketPriceService(marketTimeService, historyRepository, dailyRepository);
    }

    private StockPriceHistory intraday(String symbol, LocalDate tradingDate, int hour, int minute, String closePrice) {
        return intradayWithOpen(symbol, tradingDate, hour, minute, "100.00", closePrice);
    }

    private StockPriceHistory intradayWithOpen(
            String symbol,
            LocalDate tradingDate,
            int hour,
            int minute,
            String openPrice,
            String closePrice
    ) {
        StockPriceHistory history = new StockPriceHistory();
        history.setSymbol(symbol);
        history.setTradingDate(tradingDate);
        history.setTimestamp(tradingDate.atTime(hour, minute));
        history.setOpenPrice(new BigDecimal(openPrice));
        history.setHighPrice(new BigDecimal("105.00"));
        history.setLowPrice(new BigDecimal("99.00"));
        history.setClosePrice(new BigDecimal(closePrice));
        history.setVolume(1000L);
        history.setTimeInterval("1min");
        history.setSource("TwelveData");
        history.setCreatedAt(LocalDateTime.of(2026, 6, 15, 20, 0));
        return history;
    }

    private StockPriceDaily daily(String symbol, LocalDate tradingDate, String closePrice) {
        StockPriceDaily daily = new StockPriceDaily();
        daily.setSymbol(symbol);
        daily.setTradingDate(tradingDate);
        daily.setOpenPrice(new BigDecimal("100.00"));
        daily.setHighPrice(new BigDecimal("106.00"));
        daily.setLowPrice(new BigDecimal("98.00"));
        daily.setClosePrice(new BigDecimal(closePrice));
        daily.setVolume(5000L);
        daily.setSource("TwelveData");
        daily.setCreatedAt(LocalDateTime.of(2026, 6, 15, 19, 0));
        daily.setUpdatedAt(LocalDateTime.of(2026, 6, 15, 19, 5));
        return daily;
    }
}
