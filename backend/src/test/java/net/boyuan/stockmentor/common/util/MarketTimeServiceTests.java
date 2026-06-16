package net.boyuan.stockmentor.common.util;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTimeServiceTests {
    private final MarketTimeService marketTimeService = new MarketTimeService();

    @Test
    void treatsWeekdayTradingDayAsOpenCalendarDay() {
        assertThat(marketTimeService.isTradingDay(LocalDate.of(2026, 3, 4))).isTrue();
    }

    @Test
    void rejectsWeekendAndNyseHoliday() {
        assertThat(marketTimeService.isTradingDay(LocalDate.of(2026, 3, 7))).isFalse();
        assertThat(marketTimeService.isTradingDay(LocalDate.of(2026, 7, 3))).isFalse();
    }

    @Test
    void returnsTradingDaysBetweenDates() {
        assertThat(marketTimeService.tradingDaysBetween(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 8)
        )).containsExactly(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 3, 6)
        );
    }

    @Test
    void delayedTargetAtNineFortyFiveUsesNineThirtyMarketTime() {
        MarketTimeService service = fixedNyTime("2026-06-15T13:45:55Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.ACTIVE_DELAYED_WINDOW);
        assertThat(context.currentNyMinute()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 45));
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 30));
    }

    @Test
    void delayedTargetAtNineFortySixUsesNineThirtyOneMarketTime() {
        MarketTimeService service = fixedNyTime("2026-06-15T13:46:30Z");

        assertThat(service.getDelayedTargetMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 9, 31));
    }

    @Test
    void preDelayedOpenUsesPreviousCompletedCloseAsTargetInsteadOfOvernightTime() {
        MarketTimeService service = fixedNyTime("2026-06-15T13:40:00Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.PRE_DELAYED_OPEN);
        assertThat(context.targetTradingDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 12, 16, 0));
        assertThat(context.latestCompletedTradingDate()).isEqualTo(LocalDate.of(2026, 6, 12));
    }

    @Test
    void sixteenFourteenStillUsesActiveDelayedWindow() {
        MarketTimeService service = fixedNyTime("2026-06-15T20:14:59Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.ACTIVE_DELAYED_WINDOW);
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 15, 59));
    }

    @Test
    void sixteenFifteenStartsPostDelayedCloseBehavior() {
        MarketTimeService service = fixedNyTime("2026-06-15T20:15:00Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.POST_DELAYED_CLOSE);
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 16, 0));
        assertThat(context.latestCompletedTradingDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void afterDelayedCloseClampsTargetToMarketClose() {
        MarketTimeService service = fixedNyTime("2026-06-15T20:30:00Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.POST_DELAYED_CLOSE);
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 15, 16, 0));
        assertThat(context.latestCompletedTradingDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void weekendUsesLatestCompletedTradingDayWithoutFakeCurrentDayTarget() {
        MarketTimeService service = fixedNyTime("2026-06-13T16:00:00Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.NON_TRADING_DAY);
        assertThat(context.targetTradingDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2026, 6, 12, 16, 0));
    }

    @Test
    void holidayUsesLatestCompletedTradingDayWithoutFakeCurrentDayTarget() {
        MarketTimeService service = fixedNyTime("2026-01-01T17:00:00Z");

        MarketTimeService.DelayedMarketSessionContext context = service.getDelayedMarketSessionContext();

        assertThat(context.session()).isEqualTo(MarketTimeService.DelayedMarketSession.NON_TRADING_DAY);
        assertThat(context.targetTradingDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(context.latestCompletedTradingDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(context.targetDisplayMarketTime()).isEqualTo(LocalDateTime.of(2025, 12, 31, 16, 0));
    }

    private MarketTimeService fixedNyTime(String instant) {
        return new MarketTimeService(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
