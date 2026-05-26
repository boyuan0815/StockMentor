package net.boyuan.stockmentor.market.stock.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminBackfillRequest(
        String type,
        List<String> symbols,
        LocalDate date,
        LocalDate startDate,
        LocalDate endDate
) {
}
