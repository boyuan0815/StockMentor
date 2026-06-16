package net.boyuan.stockmentor.scheduler;

import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockSchedulerTests {
    @Mock
    private StockService stockService;
    @Mock
    private MarketTimeService marketTimeService;

    @Test
    void schedulerKeepsExistingJobsAndAddsEarlyDailyAttempt() throws Exception {
        assertThat(cron("fetchMarketOpenSessionData")).isEqualTo("0 32/5 9 * * MON-FRI");
        assertThat(cron("fetchIntradaySessionData")).isEqualTo("0 2/5 10-15 * * MON-FRI");
        assertThat(cron("fetchPostMarketFinalSnapshot")).isEqualTo("0 2,7,12 16 * * MON-FRI");
        assertThat(cron("fetchEarlyDailyAfterMarketClose")).isEqualTo("0 14 16 * * MON-FRI");
        assertThat(cron("backfillPostMarketIntradayData")).isEqualTo("0 50 16,18 * * MON-FRI");
        assertThat(cron("backfillDailyAfterMarketClose")).isEqualTo("0 00 19 * * MON-FRI");
        assertThat(cron("weekendMissingDailyBackfill")).isEqualTo("0 15 2 * * SAT");
    }

    @Test
    void earlyDailyAttemptRefreshesTodayOnlyOnTradingDays() {
        StockScheduler scheduler = new StockScheduler(stockService, marketTimeService);
        when(marketTimeService.isTradingDay()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(scheduler, "fetchEarlyDailyAfterMarketClose");

        verify(stockService).refreshDailyForDate(eq(StockMetadata.SYMBOLS), any(LocalDate.class));
    }

    @Test
    void earlyDailyAttemptSkipsNonTradingDays() {
        StockScheduler scheduler = new StockScheduler(stockService, marketTimeService);
        when(marketTimeService.isTradingDay()).thenReturn(false);

        ReflectionTestUtils.invokeMethod(scheduler, "fetchEarlyDailyAfterMarketClose");

        verify(stockService, never()).refreshDailyForDate(eq(StockMetadata.SYMBOLS), any(LocalDate.class));
    }

    @Test
    void nineteenHundredDailyJobRefreshesTodayOnlyOnTradingDays() {
        StockScheduler scheduler = new StockScheduler(stockService, marketTimeService);
        when(marketTimeService.isTradingDay()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(scheduler, "backfillDailyAfterMarketClose");

        verify(stockService).refreshDailyForDate(eq(StockMetadata.SYMBOLS), any(LocalDate.class));
    }

    @Test
    void weekendCatchupRemainsMissingOnlyAndRunsSafeIntradayCleanup() {
        StockScheduler scheduler = new StockScheduler(stockService, marketTimeService);

        ReflectionTestUtils.invokeMethod(scheduler, "weekendMissingDailyBackfill");

        verify(stockService).backfillMissingDaily(eq(StockMetadata.SYMBOLS), any(LocalDate.class), any(LocalDate.class));
        verify(stockService).cleanupOldIntradayData(14);
    }

    private String cron(String methodName) throws Exception {
        Method method = StockScheduler.class.getDeclaredMethod(methodName);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("America/New_York");
        return scheduled.cron();
    }
}
