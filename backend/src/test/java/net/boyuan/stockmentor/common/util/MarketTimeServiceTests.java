package net.boyuan.stockmentor.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
}
