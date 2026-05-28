package net.boyuan.stockmentor.common.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class MarketTimeService {
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    public boolean isMarketTradingTime() {
        LocalTime time = LocalTime.now(NY_ZONE);
        LocalTime open = LocalTime.of(9, 30);
        LocalTime close = LocalTime.of(16, 0);

        return !time.isBefore(open) && !time.isAfter(close);
    }

    public boolean isWeekday() {
        return isWeekday(LocalDate.now(NY_ZONE));
    }

    public boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;

    }

    public boolean isNonHoliday() {
        return isNonHoliday(LocalDate.now(NY_ZONE));
    }

    public boolean isNonHoliday(LocalDate date) {
        return !isNyseHoliday(date);
    }

    public boolean isTradingDay(){
        return isTradingDay(LocalDate.now(NY_ZONE));
    }

    public boolean isTradingDay(LocalDate date) {
        return isWeekday(date) && isNonHoliday(date);
    }

    public boolean isMarketOpen() {
        return isMarketTradingTime() && isWeekday() && isNonHoliday();
    }

    public List<LocalDate> tradingDaysBetween(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate cursor = startDate;

        while (!cursor.isAfter(endDate)) {
            if (isTradingDay(cursor)) {
                tradingDays.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        return tradingDays;
    }

    public List<LocalDate> latestTradingDays(int count) {
        return latestTradingDays(count, LocalDate.now(NY_ZONE));
    }

    public List<LocalDate> latestTradingDays(int count, LocalDate referenceDate) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate cursor = referenceDate;

        while (tradingDays.size() < count) {
            if (isTradingDay(cursor)) {
                tradingDays.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }

        tradingDays.sort(LocalDate::compareTo);
        return tradingDays;
    }

    private boolean isNyseHoliday(LocalDate date) {
        int year = date.getYear();

        return date.equals(observedFixedHoliday(year, 1, 1))
                || date.equals(nthWeekdayOfMonth(year, 1, DayOfWeek.MONDAY, 3))
                || date.equals(nthWeekdayOfMonth(year, 2, DayOfWeek.MONDAY, 3))
                || date.equals(easterSunday(year).minusDays(2))
                || date.equals(lastWeekdayOfMonth(year, 5, DayOfWeek.MONDAY))
                || date.equals(observedFixedHoliday(year, 6, 19))
                || date.equals(observedFixedHoliday(year, 7, 4))
                || date.equals(nthWeekdayOfMonth(year, 9, DayOfWeek.MONDAY, 1))
                || date.equals(nthWeekdayOfMonth(year, 11, DayOfWeek.THURSDAY, 4))
                || date.equals(observedFixedHoliday(year, 12, 25));
    }

    private LocalDate observedFixedHoliday(int year, int month, int day) {
        LocalDate holiday = LocalDate.of(year, month, day);
        if (holiday.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return holiday.minusDays(1);
        }
        if (holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return holiday.plusDays(1);
        }
        return holiday;
    }

    private LocalDate nthWeekdayOfMonth(int year, int month, DayOfWeek dayOfWeek, int occurrence) {
        LocalDate date = LocalDate.of(year, month, 1);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date.plusWeeks(occurrence - 1L);
    }

    private LocalDate lastWeekdayOfMonth(int year, int month, DayOfWeek dayOfWeek) {
        LocalDate date = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.minusDays(1);
        }
        return date;
    }

    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
