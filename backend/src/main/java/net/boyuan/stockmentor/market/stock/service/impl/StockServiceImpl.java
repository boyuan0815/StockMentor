package net.boyuan.stockmentor.market.stock.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.market.stock.service.StockApiClient;
import net.boyuan.stockmentor.market.stock.service.StockHistoryBuilder;
import net.boyuan.stockmentor.market.stock.service.StockSnapshotUpdater;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.StockService;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
    private final StockApiClient stockApiClient;
    private final StockHistoryBuilder stockHistoryBuilder;
    private final StockSnapshotUpdater stockSnapshotUpdater;

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    @Override
    public void fetchAndSave(String symbols) {
        fetchAndSave(symbols, 30);
    }

    @Override
    public void fetchAndSave(String symbols, int outputSize) {
        try {
            log.info("Fetching TwelveData: symbols={}, outputSize={}", symbols, outputSize);

            JsonNode root = stockApiClient.fetchTimeSeries(symbols, outputSize);

            if (root.has("code")) {
                String code = root.path("code").asText();
                String message = root.path("message").asText("Unknown error");

                log.error("API Error: code={}, message={}", code, message);
            }

            List<String> symbolList = new ArrayList<>();
            root.fieldNames().forEachRemaining(symbolList::add);

            Map<String, Stock> stockMap = stockRepository.findBySymbolIn(symbolList)
                    .stream()
                    .collect(Collectors.toMap(Stock::getSymbol, s -> s));

            List<StockPriceHistory> stockPriceHistoriesToSave = new ArrayList<>();
            LocalDate marketDate = LocalDate.now(NY_ZONE);

            root.properties().forEach(entry -> {
                String symbol = entry.getKey();
                JsonNode stockNode = entry.getValue();

                if (!stockNode.has("values")) {
                    log.warn("Symbol={} returned no values from API", symbol);
                    return;
                }

                JsonNode values = stockNode.get("values");

                Stock stock = stockMap.computeIfAbsent(symbol, key -> {
                   Stock s = new Stock();
                    s.setSymbol(symbol);
                    s.setCreatedAt(LocalDateTime.now());
                    s.setSource("TwelveData");
                    s.setTimezone("America/New_York");
                    return s;
                });

                LocalDateTime start = LocalDateTime.parse(
                        values.get(0).get("datetime").asText().replace(" ", "T")
                );

                LocalDateTime end = LocalDateTime.parse(
                        values.get(values.size() - 1).get("datetime").asText().replace(" ", "T")
                );

                List<LocalDateTime> existingTimestamps = historyRepository.findExistingTimestamps(
                        symbol,
                        "1min",
                        start,
                        end
                );

                Set<LocalDateTime> existingTimestampSet = new HashSet<>(existingTimestamps);

                List<JsonNode> newValues = stockHistoryBuilder.filterNewValues(values, existingTimestampSet, marketDate);

                stockPriceHistoriesToSave.addAll(
                        stockHistoryBuilder.buildHistoryEntities(stock, symbol, newValues)
                );

                if (!newValues.isEmpty()) {
                    stockSnapshotUpdater.updateStock(stock, newValues);
                }
            });

            if (!stockMap.isEmpty()) {
                stockRepository.saveAll(stockMap.values());
            }

            if (!stockPriceHistoriesToSave.isEmpty()) {
                historyRepository.saveAll(stockPriceHistoriesToSave);
                log.info("Saved {} new stock history rows at America/New_York time: {}", stockPriceHistoriesToSave.size(), ZonedDateTime.now(NY_ZONE));
            } else {
                log.info("No new stock history rows to save");
            }
        } catch (WebClientRequestException e) {
                log.error("TwelveData connection error: {}", e.getMessage(), e);
        } catch (WebClientResponseException e) {
                log.error("TwelveData HTTP error: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
        } catch (Exception e) {
                log.error("Failed to fetch and save stock data for symbols={}", symbols, e);
        }
    }


}