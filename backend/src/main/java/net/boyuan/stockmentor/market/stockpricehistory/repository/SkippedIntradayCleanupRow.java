package net.boyuan.stockmentor.market.stockpricehistory.repository;

import java.time.LocalDate;

public interface SkippedIntradayCleanupRow {
    String getSymbol();
    LocalDate getTradingDate();
    Long getRowCount();
}
