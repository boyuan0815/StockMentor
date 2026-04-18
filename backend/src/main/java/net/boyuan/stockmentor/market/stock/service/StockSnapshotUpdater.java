package net.boyuan.stockmentor.market.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static net.boyuan.stockmentor.common.util.StockMetadata.COMPANY_MAP;

@Service
@RequiredArgsConstructor
public class StockSnapshotUpdater {
    private final MarketTimeService marketTimeService;
    private static final Logger log = LoggerFactory.getLogger(StockSnapshotUpdater.class);

    public void updateStock(Stock stock, List<JsonNode> newValues) {

        if (newValues == null || newValues.isEmpty()) {
            return;
        }
        JsonNode latest = newValues.get(newValues.size() - 1);

        BigDecimal close = new BigDecimal(latest.get("close").asText());

        LocalDateTime marketTime = LocalDateTime.parse(
                latest.get("datetime").asText().replace(" ", "T")
        );
        LocalDate currentDate = marketTime.toLocalDate();

        boolean isNewDay = stock.getLastUpdated() == null || !stock.getLastUpdated().toLocalDate().equals(currentDate);

        if (isNewDay) {
            stock.setDayHigh(null);
            stock.setDayLow(null);
            stock.setVolume(0L);
        }

        if (stock.getDayOpen() == null || isNewDay) {
            BigDecimal dayOpen = new BigDecimal(newValues.get(0).get("open").asText());
            stock.setDayOpen(dayOpen);
        }

        String companyName = COMPANY_MAP.getOrDefault(stock.getSymbol(), stock.getSymbol());
        if (!companyName.equals(stock.getCompanyName())) {
            stock.setCompanyName(companyName);
        }

        BigDecimal batchHigh = null;
        BigDecimal batchLow = null;
        long batchVolume = 0L;

        for (JsonNode v : newValues) {
            BigDecimal high = new BigDecimal(v.get("high").asText());
            BigDecimal low = new BigDecimal(v.get("low").asText());
            long volume = v.get("volume").asLong();

            batchHigh = (batchHigh == null) ? high : batchHigh.max(high);
            batchLow = (batchLow == null) ? low : batchLow.min(low);
            batchVolume += volume;
        }

        stock.setDayHigh(stock.getDayHigh() == null ? batchHigh : stock.getDayHigh().max(batchHigh));
        stock.setDayLow(stock.getDayLow() == null ? batchLow : stock.getDayLow().min(batchLow));
        stock.setVolume(stock.getVolume() == null ? batchVolume : stock.getVolume() + batchVolume);

        if (stock.getDayOpen() != null) {
            BigDecimal percentChange = close
                    .subtract(stock.getDayOpen())
                    .divide(stock.getDayOpen(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stock.setPercentChange(percentChange);
        } else {
            log.warn("dayOpen is null for symbol = {}, skipping percentChange", stock.getSymbol());
        }

        stock.setIsMarketOpen(marketTimeService.isMarketOpen());
        stock.setCurrentPrice(close);
        stock.setLastUpdated(marketTime);
        stock.setUpdatedAt(LocalDateTime.now());
    }
}
