package net.boyuan.stockmentor.common.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

@Component
public class MarketTimeService {
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final int CURRENT_YEAR = LocalDate.now(NY_ZONE).getYear();

    public boolean isMarketTradingTime() {
        LocalTime time = LocalTime.now(NY_ZONE);
        LocalTime open = LocalTime.of(9, 30);
        LocalTime close = LocalTime.of(16, 0);

        return !time.isBefore(open) && !time.isAfter(close);
    }

    public boolean isWeekday() {
        DayOfWeek day = LocalDate.now(NY_ZONE).getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;

    }

    private static final Set<LocalDate> HOLIDAYS = Set.of(
            LocalDate.of(CURRENT_YEAR, 1, 1),
            LocalDate.of(CURRENT_YEAR, 7, 4),
            LocalDate.of(CURRENT_YEAR, 12, 25)
    );

    public boolean isNonHoliday() {
        return !HOLIDAYS.contains(LocalDate.now(NY_ZONE));
    }

    public boolean isTradingDay(){
        return isWeekday() && isNonHoliday();
    }

    public boolean isMarketOpen() {
        return isMarketTradingTime() && isWeekday() && isNonHoliday();
    }
}
