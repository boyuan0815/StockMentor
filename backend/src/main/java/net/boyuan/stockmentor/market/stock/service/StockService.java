package net.boyuan.stockmentor.market.stock.service;

import net.boyuan.stockmentor.market.stock.dto.BackfillResultDto;

import java.time.LocalDate;
import java.util.List;

public interface StockService {

//    Java not support default parameters
    void fetchAndSave(String symbols);
    void fetchAndSave(String symbols, int outputSize);
    void fetchLatestIntraday(String symbols);
    BackfillResultDto backfillIntradayForDate(String symbols, LocalDate date);
    BackfillResultDto backfillDailyRange(String symbols, LocalDate startDate, LocalDate endDate);
    BackfillResultDto backfillMissingDaily(String symbols, LocalDate startDate, LocalDate endDate);
    BackfillResultDto refreshDailyForDate(String symbols, LocalDate date);
    BackfillResultDto cleanupOldIntradayData(int retentionDays);
    List<String> splitSymbols(String symbols);
}
