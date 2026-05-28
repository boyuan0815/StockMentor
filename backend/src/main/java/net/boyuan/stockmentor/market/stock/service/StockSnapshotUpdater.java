package net.boyuan.stockmentor.market.stock.service;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static net.boyuan.stockmentor.common.util.StockMetadata.COMPANY_MAP;

@Service
@RequiredArgsConstructor
public class StockSnapshotUpdater {
    private final MarketTimeService marketTimeService;

    public void recomputeStockFromIntradayHistory(Stock stock, List<StockPriceHistory> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        List<StockPriceHistory> sortedRows = rows.stream()
                .filter(row -> row.getTimestamp() != null)
                .sorted(Comparator.comparing(StockPriceHistory::getTimestamp))
                .toList();

        if (sortedRows.isEmpty()) {
            return;
        }

        StockPriceHistory first = sortedRows.get(0);
        StockPriceHistory latest = sortedRows.get(sortedRows.size() - 1);

        BigDecimal dayHigh = sortedRows.stream()
                .map(StockPriceHistory::getHighPrice)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(latest.getHighPrice());

        BigDecimal dayLow = sortedRows.stream()
                .map(StockPriceHistory::getLowPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(latest.getLowPrice());

        long volume = sortedRows.stream()
                .map(StockPriceHistory::getVolume)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        String companyName = COMPANY_MAP.getOrDefault(stock.getSymbol(), stock.getSymbol());
        if (!companyName.equals(stock.getCompanyName())) {
            stock.setCompanyName(companyName);
        }

        stock.setDayOpen(first.getOpenPrice());
        stock.setDayHigh(dayHigh);
        stock.setDayLow(dayLow);
        stock.setVolume(volume);
        stock.setCurrentPrice(latest.getClosePrice());
        stock.setLastUpdated(latest.getTimestamp());
        stock.setIsMarketOpen(marketTimeService.isMarketOpen());
        stock.setUpdatedAt(LocalDateTime.now());

        if (first.getOpenPrice() != null && BigDecimal.ZERO.compareTo(first.getOpenPrice()) != 0
                && latest.getClosePrice() != null) {
            BigDecimal percentChange = latest.getClosePrice()
                    .subtract(first.getOpenPrice())
                    .divide(first.getOpenPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stock.setPercentChange(percentChange);
        } else {
            stock.setPercentChange(null);
        }
    }
}
