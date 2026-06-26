package net.boyuan.stockmentor.market.stock.service;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.common.util.MarketTimeService.DelayedMarketSession;
import net.boyuan.stockmentor.common.util.MarketTimeService.DelayedMarketSessionContext;
import net.boyuan.stockmentor.market.stock.model.DelayedIntradayHistorySelection;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DelayedMarketPriceService {
    public static final String INTRADAY_PRICE_SOURCE = "stock_price_history_1min";
    public static final String DAILY_PRICE_SOURCE = "stock_price_daily";

    private static final String ONE_MINUTE_INTERVAL = "1min";
    private static final int ACTIVE_WINDOW_MAX_STALENESS_MINUTES = 10;
    private static final int PERCENT_SCALE = 4;
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MarketTimeService marketTimeService;
    private final StockPriceHistoryRepository historyRepository;
    private final StockPriceDailyRepository dailyRepository;

    public DelayedMarketPrice resolveForDisplay(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        DelayedMarketSessionContext context = marketTimeService.getDelayedMarketSessionContext();

        return switch (context.session()) {
            case ACTIVE_DELAYED_WINDOW -> resolveActiveDelayedWindow(normalizedSymbol, context);
            case PRE_DELAYED_OPEN -> resolvePreDelayedOpen(normalizedSymbol, context);
            case POST_DELAYED_CLOSE -> resolvePostDelayedClose(normalizedSymbol, context);
            case NON_TRADING_DAY -> resolveNonTradingDay(normalizedSymbol, context);
        };
    }

    public DelayedIntradayHistorySelection loadOneDayHistoryForDisplay(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        DelayedMarketSessionContext context = marketTimeService.getDelayedMarketSessionContext();
        DelayedMarketPrice metadata = resolveForDisplay(normalizedSymbol);

        List<StockPriceHistory> rows = historyRepository
                .findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
                        normalizedSymbol,
                        context.targetTradingDate(),
                        ONE_MINUTE_INTERVAL,
                        context.targetDisplayMarketTime()
                );

        return new DelayedIntradayHistorySelection(rows, metadata);
    }

    private DelayedMarketPrice resolveActiveDelayedWindow(String symbol, DelayedMarketSessionContext context) {
        Optional<StockPriceHistory> selectedHistory = findLatestValidIntradayAtOrBefore(
                symbol,
                context.targetTradingDate(),
                context.targetDisplayMarketTime()
        );
        if (selectedHistory.isPresent()) {
            StockPriceHistory history = selectedHistory.get();
            long stalenessMinutes = Duration.between(history.getTimestamp(), context.targetDisplayMarketTime()).toMinutes();
            if (stalenessMinutes == 0) {
                return intradayPrice(
                        symbol,
                        history,
                        context,
                        DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                        true,
                        "Prices shown are delayed by about 15 minutes. Practice trades use StockMentor's delayed stored price, not a live market quote."
                );
            }
            if (stalenessMinutes <= ACTIVE_WINDOW_MAX_STALENESS_MINUTES) {
                return intradayPrice(
                        symbol,
                        history,
                        context,
                        DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                        true,
                        "Latest stored delayed price is slightly before the displayed market target. Practice trades use StockMentor's delayed stored price, not a live market quote."
                );
            }
        }

        return dailyFallback(
                symbol,
                context.latestCompletedTradingDate(),
                context,
                DelayedPriceFreshnessStatus.LATEST_STORED_PRICE,
                false,
                "Delayed intraday price is not available yet. Showing latest stored daily close for display only."
        ).orElseGet(() -> unavailable(
                symbol,
                context,
                DelayedPriceFreshnessStatus.UNAVAILABLE,
                "Delayed market price is not available yet. Please try again later."
        ));
    }

    private DelayedMarketPrice resolvePreDelayedOpen(String symbol, DelayedMarketSessionContext context) {
        return dailyFallback(
                symbol,
                context.latestCompletedTradingDate(),
                context,
                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE,
                true,
                "Today's delayed market display starts around 9:45 AM New York time. Showing the latest stored daily close."
        ).orElseGet(() -> unavailable(
                symbol,
                context,
                DelayedPriceFreshnessStatus.UNAVAILABLE,
                "Today's delayed market display starts around 9:45 AM New York time, and no latest stored daily close is available yet."
        ));
    }

    private DelayedMarketPrice resolvePostDelayedClose(String symbol, DelayedMarketSessionContext context) {
        Optional<DelayedMarketPrice> daily = dailyFallback(
                symbol,
                context.targetTradingDate(),
                context,
                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE,
                true,
                "Market is closed. This practice trade uses the latest stored daily close, not a live quote."
        );
        if (daily.isPresent()) {
            return daily.get();
        }

        Optional<StockPriceHistory> selectedHistory = findLatestValidIntradayAtOrBefore(
                symbol,
                context.targetTradingDate(),
                context.targetTradingDate().atTime(MARKET_CLOSE)
        );
        return selectedHistory
                .map(history -> intradayPrice(
                        symbol,
                        history,
                        context,
                        DelayedPriceFreshnessStatus.LATEST_STORED_PRICE,
                        true,
                        "Market is closed. Today's daily close is not available yet, so this practice trade uses the latest stored intraday close."
                ))
                .orElseGet(() -> unavailable(
                        symbol,
                        context,
                        DelayedPriceFreshnessStatus.UNAVAILABLE,
                        "Market is closed, and today's delayed stored price is unavailable."
                ));
    }

    private DelayedMarketPrice resolveNonTradingDay(String symbol, DelayedMarketSessionContext context) {
        return dailyFallback(
                symbol,
                context.latestCompletedTradingDate(),
                context,
                DelayedPriceFreshnessStatus.MARKET_CLOSED_LAST_CLOSE,
                true,
                "Market is closed. Prices shown are based on the latest completed trading day."
        ).orElseGet(() -> unavailable(
                symbol,
                context,
                DelayedPriceFreshnessStatus.UNAVAILABLE,
                "Market is closed, and the latest completed trading-day close is unavailable."
        ));
    }

    private Optional<StockPriceHistory> findLatestValidIntradayAtOrBefore(
            String symbol,
            LocalDate tradingDate,
            LocalDateTime targetDisplayMarketTime
    ) {
        return historyRepository
                .findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
                        symbol,
                        tradingDate,
                        ONE_MINUTE_INTERVAL,
                        targetDisplayMarketTime,
                        ZERO
                );
    }

    private DelayedMarketPrice intradayPrice(
            String symbol,
            StockPriceHistory history,
            DelayedMarketSessionContext context,
            DelayedPriceFreshnessStatus status,
            boolean tradeExecutable,
            String note
    ) {
        PriceMovement movement = priceMovement(symbol, history.getTradingDate(), history.getClosePrice());
        return new DelayedMarketPrice(
                symbol,
                history.getClosePrice(),
                movement.displayedPercentChange(),
                history.getTimestamp(),
                context.targetDisplayMarketTime(),
                context.dataDelayMinutes(),
                status,
                true,
                tradeExecutable,
                note,
                INTRADAY_PRICE_SOURCE,
                context.marketTimeZone(),
                history.getCreatedAt(),
                history.getTradingDate(),
                movement.previousClose(),
                movement.displayedAbsoluteChange(),
                status.label()
        );
    }

    private Optional<DelayedMarketPrice> dailyFallback(
            String symbol,
            LocalDate tradingDate,
            DelayedMarketSessionContext context,
            DelayedPriceFreshnessStatus status,
            boolean tradeExecutable,
            String note
    ) {
        return dailyRepository.findBySymbolAndTradingDate(symbol, tradingDate)
                .filter(this::hasPositiveClose)
                .map(daily -> {
                    PriceMovement movement = priceMovement(symbol, daily.getTradingDate(), daily.getClosePrice());
                    return new DelayedMarketPrice(
                            symbol,
                            daily.getClosePrice(),
                            movement.displayedPercentChange(),
                            daily.getTradingDate().atTime(MARKET_CLOSE),
                            context.targetDisplayMarketTime(),
                            context.dataDelayMinutes(),
                            status,
                            true,
                            tradeExecutable,
                            note,
                            DAILY_PRICE_SOURCE,
                            context.marketTimeZone(),
                            daily.getUpdatedAt() == null ? daily.getCreatedAt() : daily.getUpdatedAt(),
                            daily.getTradingDate(),
                            movement.previousClose(),
                            movement.displayedAbsoluteChange(),
                            status.label()
                    );
                });
    }

    private DelayedMarketPrice unavailable(
            String symbol,
            DelayedMarketSessionContext context,
            DelayedPriceFreshnessStatus status,
            String note
    ) {
        return new DelayedMarketPrice(
                symbol,
                null,
                null,
                null,
                context.targetDisplayMarketTime(),
                context.dataDelayMinutes(),
                status,
                false,
                false,
                note,
                null,
                context.marketTimeZone(),
                null,
                context.targetTradingDate(),
                null,
                null,
                status.label()
        );
    }

    private PriceMovement priceMovement(String symbol, LocalDate tradingDate, BigDecimal displayedPrice) {
        if (displayedPrice == null || tradingDate == null) {
            return new PriceMovement(null, null, null);
        }
        return dailyRepository.findTopBySymbolAndTradingDateBeforeOrderByTradingDateDesc(symbol, tradingDate)
                .map(StockPriceDaily::getClosePrice)
                .filter(previousClose -> previousClose.compareTo(ZERO) > 0)
                .map(previousClose -> {
                    BigDecimal absoluteChange = displayedPrice.subtract(previousClose);
                    BigDecimal percentChange = percentChange(previousClose, displayedPrice);
                    return new PriceMovement(previousClose, absoluteChange, percentChange);
                })
                .orElse(new PriceMovement(null, null, null));
    }

    private BigDecimal percentChange(BigDecimal previousClose, BigDecimal displayedPrice) {
        if (previousClose == null || previousClose.compareTo(ZERO) <= 0 || displayedPrice == null) {
            return null;
        }
        return displayedPrice
                .subtract(previousClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClose, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private boolean hasPositiveClose(StockPriceDaily daily) {
        return daily.getClosePrice() != null && daily.getClosePrice().compareTo(ZERO) > 0;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record PriceMovement(
            BigDecimal previousClose,
            BigDecimal displayedAbsoluteChange,
            BigDecimal displayedPercentChange
    ) {
    }
}
