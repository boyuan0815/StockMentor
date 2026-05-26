package net.boyuan.stockmentor.analysis.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.model.AnalysisTimeframe;
import net.boyuan.stockmentor.analysis.model.PriceCandle;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import net.boyuan.stockmentor.common.exception.ResourceNotFoundException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    private static final int COMPLETE_INTRADAY_THRESHOLD = 380;

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
                Price Consistency: %s
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
                snapshot.getPriceConsistency()
        );
    }

    private SnapshotInput loadSnapshotInput(String symbol, AnalysisTimeframe timeframe) {
        if (timeframe == AnalysisTimeframe.ONE_DAY) {
            return loadOneDayInput(symbol);
        }

        List<StockPriceDaily> dailyRows = dailyRepository.findLatestBySymbol(
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
        return new SnapshotInput(candles, "stock_price_daily", missingDataCount);
    }

    private SnapshotInput loadOneDayInput(String symbol) {
        StockPriceDaily latestDaily = dailyRepository.findTopBySymbolOrderByTradingDateDesc(symbol).orElse(null);
        LocalDate candidateDate = latestDaily == null
                ? historyRepository.findTopBySymbolOrderByTimestampDesc(symbol)
                .map(row -> row.getTimestamp().toLocalDate())
                .orElse(null)
                : latestDaily.getTradingDate();

        if (candidateDate == null) {
            throw new ResourceNotFoundException("No price data found for symbol=" + symbol);
        }

        LocalDateTime start = candidateDate.atStartOfDay();
        LocalDateTime end = candidateDate.atTime(LocalTime.MAX);
        long intradayRows = historyRepository.countBySymbolAndTimestampBetween(symbol, start, end);

        if (intradayRows >= COMPLETE_INTRADAY_THRESHOLD) {
            List<StockPriceHistory> rows = historyRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);
            return new SnapshotInput(List.of(toCandle(candidateDate, rows)), "stock_price_history_1min", 0);
        }

        if (latestDaily == null) {
            throw new ResourceNotFoundException("No complete 1D data found for symbol=" + symbol);
        }

        int missingDataCount = intradayRows > 0 ? 0 : 1;
        return new SnapshotInput(List.of(toCandle(latestDaily)), "stock_price_daily", missingDataCount);
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

        BigDecimal percentChange = last.closePrice()
                .subtract(first.openPrice())
                .divide(first.openPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal avgClose = candles.stream()
                .map(PriceCandle::closePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 6, RoundingMode.HALF_UP);
        BigDecimal volatilityScore = avgRange
                .divide(avgClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal trendStrength = percentChange.abs();

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
        snapshot.setTrend(labelTrend(percentChange));
        snapshot.setVolatilityLabel(labelVolatility(volatilityScore));
        snapshot.setVolumeTrend(labelVolumeTrend(candles));
        snapshot.setPriceConsistency(labelPriceConsistency(candles, percentChange));
        snapshot.setRiskCategory(RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate"));
        snapshot.setDataSource(input.dataSource());
        snapshot.setMissingDataCount(input.missingDataCount());
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setSnapshotHash(hash(snapshot));
        return snapshot;
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

    private String labelTrend(BigDecimal percentChange) {
        if (percentChange.compareTo(BigDecimal.ONE) > 0) {
            return "uptrend";
        }
        if (percentChange.compareTo(BigDecimal.ONE.negate()) < 0) {
            return "downtrend";
        }
        return "sideways";
    }

    private String labelVolatility(BigDecimal volatilityScore) {
        if (volatilityScore.compareTo(BigDecimal.valueOf(2)) < 0) {
            return "low";
        }
        if (volatilityScore.compareTo(BigDecimal.valueOf(5)) < 0) {
            return "moderate";
        }
        return "high";
    }

    private String labelVolumeTrend(List<PriceCandle> candles) {
        if (candles.size() < 4) {
            return "normal volume";
        }

        int midpoint = candles.size() / 2;
        BigDecimal earlier = averageVolume(candles.subList(0, midpoint));
        BigDecimal recent = averageVolume(candles.subList(midpoint, candles.size()));
        BigDecimal change = recent.subtract(earlier)
                .divide(earlier.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (change.compareTo(BigDecimal.TEN) > 0) {
            return "above average";
        }
        if (change.compareTo(BigDecimal.TEN.negate()) < 0) {
            return "below average";
        }
        return "stable";
    }

    private BigDecimal averageVolume(List<PriceCandle> candles) {
        return candles.stream()
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 2, RoundingMode.HALF_UP);
    }

    private String labelPriceConsistency(List<PriceCandle> candles, BigDecimal percentChange) {
        if (candles.size() <= 2) {
            return percentChange.signum() >= 0 ? "steady upward movement" : "steady downward movement";
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
        if (reversalRatio <= 0.25 && percentChange.signum() >= 0) {
            return "smooth upward movement";
        }
        if (reversalRatio <= 0.25) {
            return "smooth downward movement";
        }
        if (reversalRatio <= 0.5) {
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
                String.valueOf(snapshot.getMissingDataCount())
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
            int missingDataCount
    ) {
    }
}
