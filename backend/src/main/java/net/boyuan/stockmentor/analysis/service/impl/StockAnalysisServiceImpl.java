package net.boyuan.stockmentor.analysis.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.model.AnalysisTimeframe;
import net.boyuan.stockmentor.analysis.model.PriceCandle;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import net.boyuan.stockmentor.common.exception.ResourceNotFoundException;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import static net.boyuan.stockmentor.common.util.StockMetadata.RISK_CATEGORY_MAP;

@Service
@RequiredArgsConstructor
public class StockAnalysisServiceImpl implements StockAnalysisService {
    private final StockAnalysisSnapshotRepository snapshotRepository;
    private final StockPriceDailyRepository dailyRepository;
    private final StockPriceHistoryRepository historyRepository;
    private final MarketTimeService marketTimeService;

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final BigDecimal INTRADAY_COMPLETENESS_RATIO = BigDecimal.valueOf(0.9);

    @Override
    public StockAnalysisSnapshot createOrReuseSnapshot(String symbol, String timeframeValue) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        AnalysisTimeframe timeframe = AnalysisTimeframe.from(timeframeValue);
        SnapshotInput input = loadSnapshotInput(normalizedSymbol, timeframe);
        StockAnalysisSnapshot calculated = calculateSnapshot(normalizedSymbol, timeframe, input);

        return snapshotRepository.findBySymbolAndTimeframeAndSnapshotHash(
                normalizedSymbol,
                timeframe.value(),
                calculated.getSnapshotHash()
        ).orElseGet(() -> snapshotRepository.save(calculated));
    }

    @Override
    public String buildPromptUserContent(StockAnalysisSnapshot snapshot) {
        String dataNote = Boolean.TRUE.equals(snapshot.getIsFallback())
                ? "\nData note: This analysis uses daily candle fallback because complete 1-minute intraday data was unavailable."
                : "";

        return """
                Symbol: %s
                Time frame: %s
                Current price: %s
                %s change: %s%%
                Trend: %s
                Volatility: %s
                Volume Trend: %s
                Period high: %s
                Period low: %s
                Risk category: %s
                Price Consistency: %s%s
                """.formatted(
                snapshot.getSymbol(),
                snapshot.getTimeframe(),
                format(snapshot.getCurrentPrice()),
                snapshot.getTimeframe(),
                formatSigned(snapshot.getPercentChange()),
                snapshot.getTrend(),
                snapshot.getVolatilityLabel(),
                snapshot.getVolumeTrend(),
                format(snapshot.getHighPrice()),
                format(snapshot.getLowPrice()),
                snapshot.getRiskCategory(),
                snapshot.getPriceConsistency(),
                dataNote
        );
    }

    private SnapshotInput loadSnapshotInput(String symbol, AnalysisTimeframe timeframe) {
        if (timeframe == AnalysisTimeframe.ONE_DAY) {
            return loadOneDayInput(symbol);
        }

        List<StockPriceDaily> dailyRows = dailyRepository.findBySymbolOrderByTradingDateDesc(
                symbol,
                PageRequest.of(0, timeframe.tradingDays())
        );
        if (dailyRows.isEmpty()) {
            throw new ResourceNotFoundException("No daily candle data found for symbol=" + symbol);
        }

        Collections.reverse(dailyRows);
        List<PriceCandle> candles = dailyRows.stream()
                .map(this::toCandle)
                .toList();

        int missingDataCount = Math.max(0, timeframe.tradingDays() - candles.size());
        return new SnapshotInput(candles, "stock_price_daily", missingDataCount, false);
    }

    private SnapshotInput loadOneDayInput(String symbol) {
        StockPriceDaily latestDaily = dailyRepository.findTopBySymbolOrderByTradingDateDesc(symbol).orElse(null);
        LocalDate latestDailyDate = latestDaily == null ? null : latestDaily.getTradingDate();
        LocalDate latestIntradayDate = historyRepository.findTopBySymbolOrderByTimestampDesc(symbol)
                .map(row -> row.getTimestamp().toLocalDate())
                .orElse(null);
        LocalDate candidateDate = maxDate(latestDailyDate, latestIntradayDate);

        if (candidateDate == null) {
            throw new ResourceNotFoundException("No price data found for symbol=" + symbol);
        }

        LocalDateTime start = candidateDate.atStartOfDay();
        LocalDateTime end = candidateDate.atTime(LocalTime.MAX);
        List<StockPriceHistory> intradayRows = historyRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);

        if (isCompleteEnoughIntraday(candidateDate, intradayRows.size())) {
            return new SnapshotInput(List.of(toCandle(candidateDate, intradayRows)), "stock_price_history_1min", 0, false);
        }

        StockPriceDaily candidateDaily = dailyRepository.findBySymbolAndTradingDate(symbol, candidateDate).orElse(null);
        if (candidateDaily != null) {
            return new SnapshotInput(List.of(toCandle(candidateDaily)), "stock_price_daily", estimatedMissingIntradayCount(intradayRows), true);
        }

        if (latestDaily != null) {
            return new SnapshotInput(List.of(toCandle(latestDaily)), "stock_price_daily", 0, true);
        }

        if (!intradayRows.isEmpty()) {
            throw new ResourceNotFoundException("Latest 1D intraday data is incomplete and no daily fallback exists for symbol=" + symbol);
        }

        throw new ResourceNotFoundException("No complete 1D data found for symbol=" + symbol);
    }

    private LocalDate maxDate(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private boolean isCompleteEnoughIntraday(LocalDate date, int actualRows) {
        int expectedRows = expectedIntradayRows(date);
        if (expectedRows <= 0) {
            return false;
        }

        int requiredRows = BigDecimal.valueOf(expectedRows)
                .multiply(INTRADAY_COMPLETENESS_RATIO)
                .setScale(0, RoundingMode.CEILING)
                .intValue();

        return actualRows >= requiredRows;
    }

    private int expectedIntradayRows(LocalDate date) {
        if (!marketTimeService.isTradingDay(date)) {
            return 0;
        }

        LocalDate todayNy = LocalDate.now(NY_ZONE);
        if (date.isBefore(todayNy)) {
            return 390;
        }
        if (date.isAfter(todayNy)) {
            return 0;
        }

        LocalTime nowNy = LocalTime.now(NY_ZONE);
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);

        if (nowNy.isBefore(marketOpen)) {
            return 0;
        }
        if (!nowNy.isBefore(marketClose)) {
            return 390;
        }

        return (int) Duration.between(marketOpen, nowNy).toMinutes();
    }

    private int estimatedMissingIntradayCount(List<StockPriceHistory> intradayRows) {
        if (intradayRows.isEmpty()) {
            return 1;
        }
        LocalDate date = intradayRows.get(0).getTimestamp().toLocalDate();
        int expectedRows = expectedIntradayRows(date);
        if (expectedRows <= 0) {
            return 0;
        }
        return Math.max(0, expectedRows - intradayRows.size());
    }

    private String adjustedRiskCategory(String baselineRiskCategory, BigDecimal volatilityScore, BigDecimal trendStrength, RiskSignals riskSignals) {
        int riskLevel = switch (baselineRiskCategory) {
            case "aggressive" -> 3;
            case "conservative" -> 1;
            default -> 2;
        };

        if (riskSignals.erratic() && volatilityScore.compareTo(BigDecimal.valueOf(3)) >= 0) {
            riskLevel++;
        }
        if (volatilityScore.compareTo(BigDecimal.valueOf(1.5)) < 0 && riskSignals.smooth()) {
            riskLevel--;
        }
        if (trendStrength.compareTo(BigDecimal.valueOf(8)) >= 0 && !riskSignals.smooth() && volatilityScore.compareTo(BigDecimal.valueOf(3)) >= 0) {
            riskLevel++;
        }

        int boundedRiskLevel = Math.max(1, Math.min(3, riskLevel));
        return switch (boundedRiskLevel) {
            case 1 -> "conservative";
            case 3 -> "aggressive";
            default -> "moderate";
        };
    }

    private StockAnalysisSnapshot calculateSnapshot(String symbol, AnalysisTimeframe timeframe, SnapshotInput input) {
        List<PriceCandle> candles = input.candles();
        PriceCandle first = candles.get(0);
        PriceCandle last = candles.get(candles.size() - 1);

        BigDecimal highPrice = candles.stream()
                .map(PriceCandle::highPrice)
                .max(Comparator.naturalOrder())
                .orElse(last.highPrice());
        BigDecimal lowPrice = candles.stream()
                .map(PriceCandle::lowPrice)
                .min(Comparator.naturalOrder())
                .orElse(last.lowPrice());

        BigDecimal totalVolume = candles.stream()
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgVolume = totalVolume.divide(BigDecimal.valueOf(candles.size()), 2, RoundingMode.HALF_UP);

        BigDecimal totalRange = candles.stream()
                .map(candle -> candle.highPrice().subtract(candle.lowPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgRange = totalRange.divide(BigDecimal.valueOf(candles.size()), 6, RoundingMode.HALF_UP);

        validatePositivePrice(first.openPrice(), "open", symbol);
        BigDecimal percentChange = last.closePrice()
                .subtract(first.openPrice())
                .divide(first.openPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal avgClose = candles.stream()
                .map(PriceCandle::closePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 6, RoundingMode.HALF_UP);
        validatePositivePrice(avgClose, "average close", symbol);
        BigDecimal volatilityScore = avgRange
                .divide(avgClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal trendStrength = percentChange.abs();
        RiskSignals riskSignals = calculateRiskSignals(candles, percentChange, volatilityScore);

        StockAnalysisSnapshot snapshot = new StockAnalysisSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setTimeframe(timeframe.value());
        snapshot.setDataStartDate(first.tradingDate());
        snapshot.setDataEndDate(last.tradingDate());
        snapshot.setCurrentPrice(last.closePrice());
        snapshot.setAvgVolume(avgVolume);
        snapshot.setAvgRange(avgRange);
        snapshot.setVolatilityScore(volatilityScore);
        snapshot.setTrendStrength(trendStrength);
        snapshot.setPercentChange(percentChange);
        snapshot.setHighPrice(highPrice);
        snapshot.setLowPrice(lowPrice);
        String priceConsistency = labelPriceConsistency(percentChange, volatilityScore, riskSignals);
        String baselineRiskCategory = RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate");

        snapshot.setTrend(labelTrend(percentChange, priceConsistency));
        snapshot.setVolatilityLabel(labelVolatility(volatilityScore));
        snapshot.setVolumeTrend(labelVolumeTrend(candles));
        snapshot.setPriceConsistency(priceConsistency);
        snapshot.setBaselineRiskCategory(baselineRiskCategory);
        snapshot.setRiskCategory(adjustedRiskCategory(baselineRiskCategory, volatilityScore, trendStrength, riskSignals));
        snapshot.setDataSource(input.dataSource());
        snapshot.setIsFallback(input.isFallback());
        snapshot.setMissingDataCount(input.missingDataCount());
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setSnapshotHash(hash(snapshot));
        return snapshot;
    }

    private void validatePositivePrice(BigDecimal value, String priceName, String symbol) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid " + priceName + " price for symbol=" + symbol);
        }
    }

    private PriceCandle toCandle(StockPriceDaily daily) {
        return new PriceCandle(
                daily.getTradingDate(),
                daily.getOpenPrice(),
                daily.getHighPrice(),
                daily.getLowPrice(),
                daily.getClosePrice(),
                daily.getVolume() == null ? 0L : daily.getVolume()
        );
    }

    private PriceCandle toCandle(LocalDate tradingDate, List<StockPriceHistory> rows) {
        StockPriceHistory first = rows.get(0);
        StockPriceHistory last = rows.get(rows.size() - 1);
        BigDecimal high = rows.stream()
                .map(StockPriceHistory::getHighPrice)
                .max(Comparator.naturalOrder())
                .orElse(last.getHighPrice());
        BigDecimal low = rows.stream()
                .map(StockPriceHistory::getLowPrice)
                .min(Comparator.naturalOrder())
                .orElse(last.getLowPrice());
        long volume = rows.stream()
                .map(StockPriceHistory::getVolume)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        return new PriceCandle(
                tradingDate,
                first.getOpenPrice(),
                high,
                low,
                last.getClosePrice(),
                volume
        );
    }

    private String labelTrend(BigDecimal percentChange, String priceConsistency) {
        if (percentChange.compareTo(BigDecimal.ONE) > 0) {
            return priceConsistency.contains("choppy") || priceConsistency.contains("erratic")
                    ? "volatile uptrend"
                    : "strong uptrend";
        }
        if (percentChange.compareTo(BigDecimal.ONE.negate()) < 0) {
            return priceConsistency.contains("choppy") || priceConsistency.contains("erratic")
                    ? "volatile downtrend"
                    : "strong downtrend";
        }
        return "sideways";
    }

    private String labelVolatility(BigDecimal volatilityScore) {
        if (volatilityScore.compareTo(BigDecimal.valueOf(1.5)) < 0) {
            return "very low";
        }
        if (volatilityScore.compareTo(BigDecimal.valueOf(3)) < 0) {
            return "low";
        }
        if (volatilityScore.compareTo(BigDecimal.valueOf(6)) < 0) {
            return "moderate";
        }
        return "high";
    }

    private String labelVolumeTrend(List<PriceCandle> candles) {
        if (candles.size() < 4) {
            return "normal volume";
        }

        int recentSize = Math.max(1, (int) Math.ceil(candles.size() * 0.2));
        BigDecimal recentAverage = averageVolume(candles.subList(candles.size() - recentSize, candles.size()));
        BigDecimal overallAverage = averageVolume(candles);
        BigDecimal ratio = recentAverage.divide(overallAverage.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(BigDecimal.valueOf(1.5)) > 0) {
            return "unusually high";
        }
        if (ratio.compareTo(BigDecimal.valueOf(1.1)) > 0) {
            return "increasing";
        }
        if (ratio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
            return "decreasing";
        }
        return "stable";
    }

    private BigDecimal averageVolume(List<PriceCandle> candles) {
        return candles.stream()
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 2, RoundingMode.HALF_UP);
    }

    private RiskSignals calculateRiskSignals(List<PriceCandle> candles, BigDecimal percentChange, BigDecimal volatilityScore) {
        if (candles.size() <= 2) {
            boolean sideways = percentChange.signum() == 0;
            boolean smooth = volatilityScore.compareTo(BigDecimal.valueOf(6)) < 0;
            return new RiskSignals(0, smooth, false, sideways);
        }

        List<Integer> directions = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            directions.add(candles.get(i).closePrice().compareTo(candles.get(i - 1).closePrice()));
        }

        long reversals = 0;
        for (int i = 1; i < directions.size(); i++) {
            if (directions.get(i) != 0 && directions.get(i - 1) != 0 && !directions.get(i).equals(directions.get(i - 1))) {
                reversals++;
            }
        }

        double reversalRatio = reversals / (double) Math.max(1, directions.size() - 1);
        boolean sideways = percentChange.abs().compareTo(BigDecimal.ONE) < 0;
        boolean smooth = reversalRatio <= 0.25;
        boolean erratic = reversalRatio > 0.6 || volatilityScore.compareTo(BigDecimal.valueOf(6)) >= 0;

        return new RiskSignals(reversalRatio, smooth, erratic, sideways);
    }

    private String labelPriceConsistency(BigDecimal percentChange, BigDecimal volatilityScore, RiskSignals riskSignals) {
        if (riskSignals.reversalRatio() == 0) {
            if (percentChange.signum() > 0) {
                return "steady upward movement";
            }
            if (percentChange.signum() < 0) {
                return "steady downward movement";
            }
            return "sideways movement";
        }

        if (riskSignals.sideways() && volatilityScore.compareTo(BigDecimal.valueOf(1.5)) < 0) {
            return "stable sideways movement";
        }
        if (riskSignals.erratic()) {
            return "highly erratic movement";
        }
        if (riskSignals.smooth() && percentChange.signum() > 0) {
            return "smooth upward movement";
        }
        if (riskSignals.smooth() && percentChange.signum() < 0) {
            return "smooth downward movement";
        }
        if (percentChange.signum() > 0) {
            return "choppy upward movement";
        }
        if (percentChange.signum() < 0) {
            return "choppy downward movement";
        }
        if (riskSignals.reversalRatio() <= 0.5) {
            return "moderately consistent movement";
        }
        return "mixed movement";
    }

    private String hash(StockAnalysisSnapshot snapshot) {
        String raw = String.join("|",
                snapshot.getSymbol(),
                snapshot.getTimeframe(),
                String.valueOf(snapshot.getDataStartDate()),
                String.valueOf(snapshot.getDataEndDate()),
                normalize(snapshot.getAvgVolume()),
                normalize(snapshot.getAvgRange()),
                normalize(snapshot.getVolatilityScore()),
                normalize(snapshot.getTrendStrength()),
                normalize(snapshot.getPercentChange()),
                normalize(snapshot.getHighPrice()),
                normalize(snapshot.getLowPrice()),
                snapshot.getTrend(),
                snapshot.getVolatilityLabel(),
                snapshot.getVolumeTrend(),
                snapshot.getPriceConsistency(),
                snapshot.getRiskCategory(),
                snapshot.getBaselineRiskCategory(),
                String.valueOf(snapshot.getMissingDataCount()),
                snapshot.getDataSource(),
                String.valueOf(snapshot.getIsFallback())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalize(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String format(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatSigned(BigDecimal value) {
        if (value == null) {
            return "";
        }
        String formatted = value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return value.signum() > 0 ? "+" + formatted : formatted;
    }

    private record SnapshotInput(
            List<PriceCandle> candles,
            String dataSource,
            int missingDataCount,
            boolean isFallback
    ) {
    }

    private record RiskSignals(
            double reversalRatio,
            boolean smooth,
            boolean erratic,
            boolean sideways
    ) {
    }
}
