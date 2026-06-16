package net.boyuan.stockmentor.common.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class MarketTimeService {
    public static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    public static final int DELAYED_DATA_DELAY_MINUTES = 15;

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime DELAYED_DISPLAY_OPEN = LocalTime.of(9, 45);
    private static final LocalTime DELAYED_DISPLAY_CLOSE = LocalTime.of(16, 15);

    private final Clock clock;

    public MarketTimeService() {
        this(Clock.systemUTC());
    }

    public MarketTimeService(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public boolean isMarketTradingTime() {
        LocalTime time = currentNyTime();

        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }

    public boolean isWeekday() {
        return isWeekday(currentNyDate());
    }

    public boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;

    }

    public boolean isNonHoliday() {
        return isNonHoliday(currentNyDate());
    }

    public boolean isNonHoliday(LocalDate date) {
        return !isNyseHoliday(date);
    }

    public boolean isTradingDay(){
        return isTradingDay(currentNyDate());
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
        return latestTradingDays(count, currentNyDate());
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

    public ZoneId getMarketTimeZone() {
        return NY_ZONE;
    }

    public ZonedDateTime getCurrentNewYorkDateTime() {
        return currentNyDateTime();
    }

    public LocalDate getCurrentNewYorkDate() {
        return currentNyDate();
    }

    public LocalDateTime getDelayedTargetMarketTime() {
        return getDelayedMarketSessionContext().targetDisplayMarketTime();
    }

    public boolean isDelayedDisplayWindow() {
        return getDelayedMarketSessionContext().session() == DelayedMarketSession.ACTIVE_DELAYED_WINDOW;
    }

    public DelayedMarketSessionContext getDelayedMarketSessionContext() {
        return getDelayedMarketSessionContext(currentNyDateTime());
    }

    DelayedMarketSessionContext getDelayedMarketSessionContext(ZonedDateTime currentNewYorkTime) {
        ZonedDateTime normalizedNow = currentNewYorkTime.withZoneSameInstant(NY_ZONE);
        LocalDateTime currentNyMinute = normalizedNow.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
        LocalDate currentDate = normalizedNow.toLocalDate();
        LocalTime currentTime = normalizedNow.toLocalTime();

        DelayedMarketSession session = delayedMarketSession(currentDate, currentTime);
        LocalDate latestCompletedTradingDate = latestCompletedTradingDate(currentDate, currentTime, session);
        LocalDate targetTradingDate = targetTradingDate(currentDate, latestCompletedTradingDate, session);
        LocalDateTime targetDisplayMarketTime = targetDisplayMarketTime(currentNyMinute, targetTradingDate, session);

        return new DelayedMarketSessionContext(
                normalizedNow,
                currentNyMinute,
                targetDisplayMarketTime,
                targetTradingDate,
                latestCompletedTradingDate,
                session,
                NY_ZONE.getId(),
                DELAYED_DATA_DELAY_MINUTES
        );
    }

    public LocalDate previousTradingDayBefore(LocalDate date) {
        LocalDate cursor = date.minusDays(1);
        while (!isTradingDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private DelayedMarketSession delayedMarketSession(LocalDate currentDate, LocalTime currentTime) {
        if (!isTradingDay(currentDate)) {
            return DelayedMarketSession.NON_TRADING_DAY;
        }
        if (currentTime.isBefore(DELAYED_DISPLAY_OPEN)) {
            return DelayedMarketSession.PRE_DELAYED_OPEN;
        }
        if (currentTime.isBefore(DELAYED_DISPLAY_CLOSE)) {
            return DelayedMarketSession.ACTIVE_DELAYED_WINDOW;
        }
        return DelayedMarketSession.POST_DELAYED_CLOSE;
    }

    private LocalDate latestCompletedTradingDate(
            LocalDate currentDate,
            LocalTime currentTime,
            DelayedMarketSession session
    ) {
        if (session == DelayedMarketSession.POST_DELAYED_CLOSE && !currentTime.isBefore(DELAYED_DISPLAY_CLOSE)) {
            return currentDate;
        }
        return previousTradingDayBefore(currentDate);
    }

    private LocalDate targetTradingDate(
            LocalDate currentDate,
            LocalDate latestCompletedTradingDate,
            DelayedMarketSession session
    ) {
        return switch (session) {
            case ACTIVE_DELAYED_WINDOW, POST_DELAYED_CLOSE -> currentDate;
            case PRE_DELAYED_OPEN, NON_TRADING_DAY -> latestCompletedTradingDate;
        };
    }

    private LocalDateTime targetDisplayMarketTime(
            LocalDateTime currentNyMinute,
            LocalDate targetTradingDate,
            DelayedMarketSession session
    ) {
        return switch (session) {
            case ACTIVE_DELAYED_WINDOW ->
                    currentNyMinute.minusMinutes(DELAYED_DATA_DELAY_MINUTES);
            case PRE_DELAYED_OPEN, POST_DELAYED_CLOSE, NON_TRADING_DAY -> targetTradingDate.atTime(MARKET_CLOSE);
        };
    }

    private ZonedDateTime currentNyDateTime() {
        return Instant.now(clock).atZone(NY_ZONE);
    }

    private LocalDate currentNyDate() {
        return currentNyDateTime().toLocalDate();
    }

    private LocalTime currentNyTime() {
        return currentNyDateTime().toLocalTime();
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

    public enum DelayedMarketSession {
        PRE_DELAYED_OPEN,
        ACTIVE_DELAYED_WINDOW,
        POST_DELAYED_CLOSE,
        NON_TRADING_DAY
    }

    public record DelayedMarketSessionContext(
            ZonedDateTime currentNewYorkTime,
            LocalDateTime currentNyMinute,
            LocalDateTime targetDisplayMarketTime,
            LocalDate targetTradingDate,
            LocalDate latestCompletedTradingDate,
            DelayedMarketSession session,
            String marketTimeZone,
            int dataDelayMinutes
    ) {
    }
}
