package net.boyuan.stockmentor.scheduler;

import net.boyuan.stockmentor.ai.dto.admin.AiSuggestionRefreshJobResponse;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionScheduledRefreshService;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAiSuggestionSchedulerTests {
    @Mock
    private MarketTimeService marketTimeService;
    @Mock
    private StockAiSuggestionScheduledRefreshService scheduledRefreshService;

    private StockAiSuggestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StockAiSuggestionScheduler(marketTimeService, scheduledRefreshService);
    }

    @Test
    void scheduledRefreshSkipsNonTradingDay() {
        when(marketTimeService.isTradingDay()).thenReturn(false);

        scheduler.refreshSuggestionsAfterMarketClose();

        verifyNoInteractions(scheduledRefreshService);
    }

    @Test
    void scheduledRefreshDelegatesToSharedServiceOnTradingDay() {
        when(marketTimeService.isTradingDay()).thenReturn(true);
        when(scheduledRefreshService.runScheduledRefresh(AiSuggestionRefreshTriggeredBy.SCHEDULED, null))
                .thenReturn(jobResponse());

        scheduler.refreshSuggestionsAfterMarketClose();

        verify(scheduledRefreshService).runScheduledRefresh(AiSuggestionRefreshTriggeredBy.SCHEDULED, null);
    }

    private AiSuggestionRefreshJobResponse jobResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new AiSuggestionRefreshJobResponse(
                1L,
                "SUCCESS",
                "SCHEDULED",
                null,
                now,
                now,
                1,
                0,
                1,
                0,
                0,
                0,
                "AI suggestion refresh completed successfully"
        );
    }
}
