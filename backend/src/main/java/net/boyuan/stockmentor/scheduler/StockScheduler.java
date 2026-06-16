package net.boyuan.stockmentor.scheduler;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.service.StockService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class StockScheduler {
    private final StockService stockService;
    private final MarketTimeService marketTimeService;
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final String SYMBOLS = StockMetadata.SYMBOLS;

    // 09:32 - 09:57
    @Scheduled(cron = "0 32/5 9 * * MON-FRI", zone = "America/New_York")
    private void fetchMarketOpenSessionData() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchLatestIntraday(SYMBOLS);
        }
    }

    // 10:02 - 15:57
    @Scheduled(cron = "0 2/5 10-15 * * MON-FRI", zone = "America/New_York")
    private void fetchIntradaySessionData() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchLatestIntraday(SYMBOLS);
        }
    }

    // 16:02, 16;07, 16:12
    @Scheduled(cron = "0 2,7,12 16 * * MON-FRI", zone = "America/New_York")
    private void fetchPostMarketFinalSnapshot() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchLatestIntraday(SYMBOLS);
        }
    }

    // 16:14: opportunistic early daily candle attempt; 19:00 remains the stable daily refresh fallback.
    @Scheduled(cron = "0 14 16 * * MON-FRI", zone = "America/New_York")
    private void fetchEarlyDailyAfterMarketClose() {
        if (marketTimeService.isTradingDay()) {
            LocalDate today = LocalDate.now(NY_ZONE);
            stockService.refreshDailyForDate(SYMBOLS, today);
        }
    }

    // 16:50 and 18:50: Twelve Data free-tier data may lag, so repair the full trading day.
    @Scheduled(cron = "0 50 16,18 * * MON-FRI", zone = "America/New_York")
    private void backfillPostMarketIntradayData() {
        if (marketTimeService.isTradingDay()) {
            stockService.backfillIntradayForDate(SYMBOLS, LocalDate.now(NY_ZONE));
        }
    }

    // 19:00: stable daily candle refresh after provider lag, separate from the early 16:14 attempt.
    @Scheduled(cron = "0 00 19 * * MON-FRI", zone = "America/New_York")
    private void backfillDailyAfterMarketClose() {
        if (marketTimeService.isTradingDay()) {
            LocalDate today = LocalDate.now(NY_ZONE);
            stockService.refreshDailyForDate(SYMBOLS, today);
        }
    }

    // Weekend catch-up: fill missing daily candles for the last 3 months, then safely clean old 1min rows.
    @Scheduled(cron = "0 15 2 * * SAT", zone = "America/New_York")
    private void weekendMissingDailyBackfill() {
        LocalDate today = LocalDate.now(NY_ZONE);
        stockService.backfillMissingDaily(SYMBOLS, today.minusMonths(3), today.minusDays(1));
        stockService.cleanupOldIntradayData(14);
    }
}
