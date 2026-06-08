package net.boyuan.stockmentor.market.stock.service;

import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockSnapshotUpdaterTests {
    private final StockSnapshotUpdater updater = new StockSnapshotUpdater(new MarketTimeService());

    @Test
    void recomputesSnapshotFromFullIntradayHistory() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");

        updater.recomputeStockFromIntradayHistory(stock, List.of(
                history("AAPL", "2026-05-27T15:59:00", "310.500000", "311.000000", "310.100000", "310.929990", 300L),
                history("AAPL", "2026-05-27T10:56:00", "312.470000", "313.220000", "312.000000", "312.800000", 200L),
                history("AAPL", "2026-05-27T09:30:00", "308.440000", "309.000000", "308.329990", "308.700000", 100L)
        ));

        assertThat(stock.getDayOpen()).isEqualByComparingTo("308.440000");
        assertThat(stock.getDayHigh()).isEqualByComparingTo("313.220000");
        assertThat(stock.getDayLow()).isEqualByComparingTo("308.329990");
        assertThat(stock.getVolume()).isEqualTo(600L);
        assertThat(stock.getCurrentPrice()).isEqualByComparingTo("310.929990");
        assertThat(stock.getLastUpdated()).isEqualTo(LocalDateTime.parse("2026-05-27T15:59:00"));
        assertThat(stock.getPercentChange()).isEqualByComparingTo("0.8100");
    }

    private StockPriceHistory history(
            String symbol,
            String timestamp,
            String open,
            String high,
            String low,
            String close,
            long volume
    ) {
        LocalDateTime marketTime = LocalDateTime.parse(timestamp);

        StockPriceHistory history = new StockPriceHistory();
        history.setSymbol(symbol);
        history.setTimestamp(marketTime);
        history.setTradingDate(LocalDate.from(marketTime));
        history.setOpenPrice(new BigDecimal(open));
        history.setHighPrice(new BigDecimal(high));
        history.setLowPrice(new BigDecimal(low));
        history.setClosePrice(new BigDecimal(close));
        history.setVolume(volume);
        history.setTimeInterval("1min");
        history.setSource("TwelveData");
        return history;
    }
}
