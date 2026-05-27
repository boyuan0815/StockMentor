package net.boyuan.stockmentor.market.stock.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.dto.BackfillResultDto;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.StockApiClient;
import net.boyuan.stockmentor.market.stock.service.StockHistoryBuilder;
import net.boyuan.stockmentor.market.stock.service.StockService;
import net.boyuan.stockmentor.market.stock.service.StockSnapshotUpdater;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import net.boyuan.stockmentor.market.stockpricehistory.repository.SkippedIntradayCleanupRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository historyRepository;
    private final StockPriceDailyRepository dailyRepository;
    private final StockApiClient stockApiClient;
    private final StockHistoryBuilder stockHistoryBuilder;
    private final StockSnapshotUpdater stockSnapshotUpdater;
    private final MarketTimeService marketTimeService;

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final int INTRADAY_BATCH_SIZE = 2;
    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    @Override
    public void fetchAndSave(String symbols) {
        fetchLatestIntraday(symbols);
    }

    @Override
    public void fetchAndSave(String symbols, int outputSize) {
        fetchIntraday(symbols, outputSize, LocalDate.now(NY_ZONE), true);
    }

    @Override
    public void fetchLatestIntraday(String symbols) {
        fetchIntraday(symbols, 30, LocalDate.now(NY_ZONE), true);
    }

    @Override
    public BackfillResultDto backfillIntradayForDate(String symbols, LocalDate date) {
        BackfillResultDto.Builder result = BackfillResultDto.builder("intraday-date")
                .symbols(splitSymbols(symbols))
                .startDate(date)
                .endDate(date);

        if (!marketTimeService.isTradingDay(date)) {
            return result.addMessage(date + " is not a NYSE trading day").build();
        }

        List<String> symbolList = splitSymbols(symbols);
        int savedRows = 0;
        int skippedRows = 0;

        for (int i = 0; i < symbolList.size(); i += INTRADAY_BATCH_SIZE) {
            List<String> batch = symbolList.subList(i, Math.min(i + INTRADAY_BATCH_SIZE, symbolList.size()));
            IntradaySaveStats stats = fetchIntraday(String.join(",", batch), 390, date, date.equals(LocalDate.now(NY_ZONE)));
            savedRows += stats.savedRows();
            skippedRows += stats.skippedRows();
        }

        return result.savedRows(savedRows)
                .skippedRows(skippedRows)
                .addMessage("Intraday backfill completed in max " + INTRADAY_BATCH_SIZE + "-symbol batches")
                .build();
    }

    @Override
    public BackfillResultDto backfillDailyRange(String symbols, LocalDate startDate, LocalDate endDate) {
        BackfillResultDto.Builder result = BackfillResultDto.builder("daily-range")
                .symbols(splitSymbols(symbols))
                .startDate(startDate)
                .endDate(endDate);

        try {
            JsonNode root = stockApiClient.fetchDailyRange(symbols, startDate, endDate);
            if (isRootApiError(root)) {
                return result.addMessage(apiErrorMessage(root)).build();
            }

            SaveDailyStats stats = saveDailyCandles(root);
            return result.savedRows(stats.savedRows())
                    .skippedRows(stats.skippedRows())
                    .addMessage("Daily range backfill completed")
                    .build();
        } catch (WebClientRequestException e) {
            log.error("TwelveData daily connection error: {}", e.getMessage(), e);
            return result.addMessage("TwelveData connection error: " + e.getMessage()).build();
        } catch (WebClientResponseException e) {
            log.error("TwelveData daily HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return result.addMessage("TwelveData HTTP error: " + e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Failed daily backfill for symbols={}, startDate={}, endDate={}", symbols, startDate, endDate, e);
            return result.addMessage("Daily backfill failed: " + e.getMessage()).build();
        }
    }

    @Override
    public BackfillResultDto backfillMissingDaily(String symbols, LocalDate startDate, LocalDate endDate) {
        List<LocalDate> expectedDates = marketTimeService.tradingDaysBetween(startDate, endDate);
        List<String> symbolList = splitSymbols(symbols);

        boolean anyMissing = false;
        for (String symbol : symbolList) {
            Set<LocalDate> existingDates = new HashSet<>(
                    dailyRepository.findExistingTradingDates(symbol, startDate, endDate)
            );
            if (expectedDates.stream().anyMatch(date -> !existingDates.contains(date))) {
                anyMissing = true;
                break;
            }
        }

        if (!anyMissing) {
            return BackfillResultDto.builder("daily-missing")
                    .symbols(symbolList)
                    .startDate(startDate)
                    .endDate(endDate)
                    .addMessage("No missing daily candles detected")
                    .build();
        }

        BackfillResultDto backfillResult = backfillDailyRange(symbols, startDate, endDate);
        return BackfillResultDto.builder("daily-missing")
                .symbols(symbolList)
                .startDate(startDate)
                .endDate(endDate)
                .savedRows(backfillResult.savedRows())
                .skippedRows(backfillResult.skippedRows())
                .addMessage("Missing daily candles were detected; range backfill was executed")
                .build();
    }

    @Override
    @Transactional
    public BackfillResultDto cleanupOldIntradayData(int retentionDays) {
        LocalDate cutoffDate = LocalDate.now(NY_ZONE).minusDays(retentionDays);
        LocalDateTime cutoffTimestamp = cutoffDate.atStartOfDay();
        List<StockPriceDaily> dailyCandlesAvailableForCleanup = dailyRepository.findByTradingDateBefore(cutoffDate);
        BackfillResultDto.Builder result = BackfillResultDto.builder("intraday-cleanup")
                .startDate(cutoffDate);

        long deletedRows = 0;

        for (StockPriceDaily daily : dailyCandlesAvailableForCleanup) {
            deletedRows += historyRepository.deleteBySymbolAndTradingDate(daily.getSymbol(), daily.getTradingDate());
        }

        List<SkippedIntradayCleanupRow> skippedGroups = historyRepository.findSkippedCleanupRows(cutoffTimestamp);
        long skippedRows = skippedGroups.stream()
                .map(SkippedIntradayCleanupRow::getRowCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        for (SkippedIntradayCleanupRow skippedGroup : skippedGroups) {
            log.info(
                    "Skipped 1min cleanup for symbol={}, date={} because daily candle is missing",
                    skippedGroup.getSymbol(),
                    skippedGroup.getTradingDate()
            );
        }

        return result.deletedRows((int) deletedRows)
                .skippedRows((int) skippedRows)
                .addMessage("Deleted old 1min rows only when matching daily candles exist")
                .build();
    }

    @Override
    public List<String> splitSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return List.of();
        }
        return Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private IntradaySaveStats fetchIntraday(String symbols, int outputSize, LocalDate targetDate, boolean updateSnapshot) {
        try {
            log.info("Fetching TwelveData intraday: symbols={}, outputSize={}, targetDate={}", symbols, outputSize, targetDate);

            JsonNode root = targetDate.equals(LocalDate.now(NY_ZONE)) && outputSize != 390
                    ? stockApiClient.fetchTimeSeries(symbols, outputSize)
                    : stockApiClient.fetchIntradayForDate(symbols, targetDate);

            if (isRootApiError(root)) {
                log.error(apiErrorMessage(root));
                return new IntradaySaveStats(0, 0);
            }

            List<String> symbolList = splitSymbols(symbols);
            Map<String, Stock> stockMap = getOrCreateStockMap(symbolList);
            List<StockPriceHistory> historiesToSave = new ArrayList<>();
            int skippedRows = 0;

            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                String symbol = entry.getKey().trim().toUpperCase();
                JsonNode stockNode = entry.getValue();

                if (isSymbolApiError(stockNode)) {
                    log.warn("Skipping symbol={} because TwelveData returned: {}", symbol, apiErrorMessage(stockNode));
                    continue;
                }

                JsonNode values = stockNode.get("values");
                if (values == null || !values.isArray() || values.isEmpty()) {
                    log.warn("Symbol={} returned no intraday values from API", symbol);
                    continue;
                }

                Stock stock = stockMap.get(symbol);
                LocalDateTime start = parseDateTime(values.get(0).get("datetime").asText());
                LocalDateTime end = parseDateTime(values.get(values.size() - 1).get("datetime").asText());

                Set<LocalDateTime> existingTimestampSet = new HashSet<>(
                        historyRepository.findExistingTimestamps(symbol, "1min", start, end)
                );

                List<JsonNode> newValues = stockHistoryBuilder.filterNewValues(values, existingTimestampSet, targetDate);
                skippedRows += values.size() - newValues.size();

                historiesToSave.addAll(stockHistoryBuilder.buildHistoryEntities(stock, symbol, newValues));

                if (updateSnapshot && !newValues.isEmpty()) {
                    stockSnapshotUpdater.updateStock(stock, newValues);
                }
            }

            if (!stockMap.isEmpty()) {
                stockRepository.saveAll(stockMap.values());
            }

            if (!historiesToSave.isEmpty()) {
                historyRepository.saveAll(historiesToSave);
                log.info("Saved {} new 1min rows at America/New_York time: {}", historiesToSave.size(), ZonedDateTime.now(NY_ZONE));
            } else {
                log.info("No new 1min rows to save");
            }

            return new IntradaySaveStats(historiesToSave.size(), skippedRows);
        } catch (WebClientRequestException e) {
            log.error("TwelveData intraday connection error: {}", e.getMessage(), e);
        } catch (WebClientResponseException e) {
            log.error("TwelveData intraday HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to fetch and save intraday data for symbols={}", symbols, e);
        }

        return new IntradaySaveStats(0, 0);
    }

    private SaveDailyStats saveDailyCandles(JsonNode root) {
        List<String> symbolList = new ArrayList<>();
        root.fieldNames().forEachRemaining(symbol -> symbolList.add(symbol.trim().toUpperCase()));
        Map<String, Stock> stockMap = getOrCreateStockMap(symbolList);

        List<StockPriceDaily> dailyCandlesToSave = new ArrayList<>();
        int skippedRows = 0;

        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            String symbol = entry.getKey().trim().toUpperCase();
            JsonNode stockNode = entry.getValue();

            if (isSymbolApiError(stockNode)) {
                log.warn("Skipping daily symbol={} because TwelveData returned: {}", symbol, apiErrorMessage(stockNode));
                continue;
            }

            JsonNode values = stockNode.get("values");
            if (values == null || !values.isArray() || values.isEmpty()) {
                log.warn("Symbol={} returned no daily values from API", symbol);
                continue;
            }

            Stock stock = stockMap.get(symbol);
            for (JsonNode value : values) {
                LocalDate tradingDate = LocalDate.parse(value.get("datetime").asText());
                if (dailyRepository.existsBySymbolAndTradingDate(symbol, tradingDate)) {
                    skippedRows++;
                    continue;
                }

                StockPriceDaily daily = new StockPriceDaily();
                daily.setStock(stock);
                daily.setSymbol(symbol);
                daily.setTradingDate(tradingDate);
                daily.setOpenPrice(new BigDecimal(value.get("open").asText()));
                daily.setHighPrice(new BigDecimal(value.get("high").asText()));
                daily.setLowPrice(new BigDecimal(value.get("low").asText()));
                daily.setClosePrice(new BigDecimal(value.get("close").asText()));
                daily.setVolume(value.get("volume").asLong());
                daily.setSource("TwelveData");
                daily.setCreatedAt(LocalDateTime.now());
                daily.setUpdatedAt(LocalDateTime.now());
                dailyCandlesToSave.add(daily);
            }
        }

        if (!stockMap.isEmpty()) {
            stockRepository.saveAll(stockMap.values());
        }

        if (!dailyCandlesToSave.isEmpty()) {
            dailyRepository.saveAll(dailyCandlesToSave);
        }

        return new SaveDailyStats(dailyCandlesToSave.size(), skippedRows);
    }

    private Map<String, Stock> getOrCreateStockMap(List<String> symbols) {
        Map<String, Stock> stockMap = stockRepository.findBySymbolIn(symbols)
                .stream()
                .collect(Collectors.toMap(Stock::getSymbol, stock -> stock));

        for (String symbol : symbols) {
            stockMap.computeIfAbsent(symbol, key -> {
                Stock stock = new Stock();
                stock.setSymbol(symbol);
                stock.setCreatedAt(LocalDateTime.now());
                stock.setSource("TwelveData");
                stock.setTimezone("America/New_York");
                return stock;
            });
        }

        return stockMap;
    }

    private boolean isRootApiError(JsonNode root) {
        return root != null && "error".equalsIgnoreCase(root.path("status").asText()) && root.has("code");
    }

    private boolean isSymbolApiError(JsonNode node) {
        return node != null && "error".equalsIgnoreCase(node.path("status").asText());
    }

    private String apiErrorMessage(JsonNode node) {
        return "TwelveData API error: code=" + node.path("code").asText("unknown")
                + ", message=" + node.path("message").asText("Unknown error");
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value.replace(" ", "T"));
    }

    private record IntradaySaveStats(int savedRows, int skippedRows) {
    }

    private record SaveDailyStats(int savedRows, int skippedRows) {
    }
}
