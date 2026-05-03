package net.boyuan.stockmentor.scheduler;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.service.StockService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class StockScheduler {
    private final StockService stockService;
    private final MarketTimeService marketTimeService;
    private static final String SYMBOLS = "NVDA,TSLA,AMD,AAPL,MSFT,GOOG,KO,JNJ";
    private static final String BACKFILL_GROUP_1 = "NVDA,TSLA";
    private static final String BACKFILL_GROUP_2 = "AMD,AAPL";
    private static final String BACKFILL_GROUP_3 = "MSFT,GOOG";
    private static final String BACKFILL_GROUP_4 = "KO,JNJ";

    // 09:32 - 09:57
    @Scheduled(cron = "0 32/5 9 * * MON-FRI", zone = "America/New_York")
    private void fetchMarketOpenSessionData() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(SYMBOLS);
        }
    }

    // 10:02 - 15:57
    @Scheduled(cron = "0 2/5 10-15 * * MON-FRI", zone = "America/New_York")
    private void fetchIntradaySessionData() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(SYMBOLS);
        }
    }

    // 16:02
    @Scheduled(cron = "0 2 16 * * MON-FRI", zone = "America/New_York")
    private void fetchPostMarketFinalSnapshot() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(SYMBOLS);
        }
    }

    //  16:50, 18:50
    @Scheduled(cron = "0 50 16,18 * * MON-FRI", zone = "America/New_York")
    private void backfillGroup1() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(BACKFILL_GROUP_1, 390);
        }
    }

    //  16:53, 18:53
    @Scheduled(cron = "0 53 16,18 * * MON-FRI", zone = "America/New_York")
    private void backfillGroup2() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(BACKFILL_GROUP_2, 390);
        }
    }

    //  16:56, 18:56
    @Scheduled(cron = "0 56 16,18 * * MON-FRI", zone = "America/New_York")
    private void backfillGroup3() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(BACKFILL_GROUP_3, 390);
        }
    }

    //  16:59, 18:59
    @Scheduled(cron = "0 59 16,18 * * MON-FRI", zone = "America/New_York")
    private void backfillGroup4() {
        if (marketTimeService.isTradingDay()) {
            stockService.fetchAndSave(BACKFILL_GROUP_4, 390);
        }
    }
}