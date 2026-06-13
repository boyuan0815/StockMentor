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
        String source
) {
}
