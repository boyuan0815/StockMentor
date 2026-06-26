package net.boyuan.stockmentor.market.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockHistoryPointResponse(
        LocalDateTime timestamp,
        LocalDate tradingDate,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        Long volume,
        String source,
        BigDecimal price,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close
) {
    public StockHistoryPointResponse(
            LocalDateTime timestamp,
            LocalDate tradingDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            Long volume,
            String source
    ) {
        this(timestamp, tradingDate, openPrice, highPrice, lowPrice, closePrice, volume, source,
                closePrice, openPrice, highPrice, lowPrice, closePrice);
    }
}
