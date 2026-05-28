package net.boyuan.stockmentor.analysis.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceCandle(
        LocalDate tradingDate,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        long volume
) {
}
