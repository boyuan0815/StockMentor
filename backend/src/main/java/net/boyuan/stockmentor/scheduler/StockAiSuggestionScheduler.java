package net.boyuan.stockmentor.scheduler;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.admin.AiSuggestionRefreshJobResponse;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionScheduledRefreshService;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockAiSuggestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StockAiSuggestionScheduler.class);

    private final MarketTimeService marketTimeService;
    private final StockAiSuggestionScheduledRefreshService scheduledRefreshService;

    @Scheduled(cron = "0 30 19 * * MON-FRI", zone = "America/New_York")
    void refreshSuggestionsAfterMarketClose() {
        if (!marketTimeService.isTradingDay()) {
            log.info("Scheduled AI suggestion refresh skipped because today is not a trading day");
            return;
        }

        AiSuggestionRefreshJobResponse summary = scheduledRefreshService.runScheduledRefresh(
                AiSuggestionRefreshTriggeredBy.SCHEDULED,
                null
        );
        log.info("Scheduled AI suggestion refresh summary jobId={} status={} processed={} success={} reused={} skipped={} fallback={} failed={}",
                summary.jobId(),
                summary.status(),
                summary.processedUsers(),
                summary.successCount(),
                summary.reusedCount(),
                summary.skippedUsers(),
                summary.fallbackCount(),
                summary.failedCount());
    }
}
