package net.boyuan.stockmentor.market.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class StockHistoryBuilder {
    public List<JsonNode> filterNewValues(JsonNode values, Set<LocalDateTime> existingTimestampSet, LocalDate marketDate) {
        List<JsonNode> newValues = new ArrayList<>();

        for (JsonNode v : values) {
            LocalDateTime marketTime = LocalDateTime.parse(
                    v.get("datetime").asText().replace(" ", "T")
            );
            boolean isDuplicate = existingTimestampSet.contains(marketTime);
            boolean isToday = marketTime.toLocalDate().equals(marketDate);
//            will not catch up the data from yesterday even though stock data missing
            if (isDuplicate || !isToday) {
                continue;
            }
            newValues.add(v);
        }
        return newValues;
    }

    public List<StockPriceHistory> buildHistoryEntities(Stock stock, String symbol, List<JsonNode> newValues) {

        List<StockPriceHistory> stockPriceHistoriesToSave = new ArrayList<>();

        for (JsonNode v : newValues) {
            LocalDateTime marketTime = LocalDateTime.parse(
                    v.get("datetime").asText().replace(" ", "T")
            );

            StockPriceHistory history = new StockPriceHistory();
            history.setStock(stock);
            history.setSymbol(symbol);
            history.setTimestamp(marketTime);
            history.setOpenPrice(new BigDecimal(v.get("open").asText()));
            history.setHighPrice(new BigDecimal(v.get("high").asText()));
            history.setLowPrice(new BigDecimal(v.get("low").asText()));
            history.setClosePrice(new BigDecimal(v.get("close").asText()));
            history.setVolume(v.get("volume").asLong());
            history.setTimeInterval("1min");
            history.setSource("TwelveData");
            history.setCreatedAt(LocalDateTime.now());

            stockPriceHistoriesToSave.add(history);
        }

        return stockPriceHistoriesToSave;
    }
}
