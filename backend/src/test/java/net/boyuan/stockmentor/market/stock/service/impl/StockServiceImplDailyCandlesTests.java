package net.boyuan.stockmentor.market.stock.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.market.stock.dto.BackfillResultDto;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.StockApiClient;
import net.boyuan.stockmentor.market.stock.service.StockHistoryBuilder;
import net.boyuan.stockmentor.market.stock.service.StockSnapshotUpdater;
import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import net.boyuan.stockmentor.market.stockdaily.repository.StockPriceDailyRepository;
import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

@ExtendWith(MockitoExtension.class)
class StockServiceImplDailyCandlesTests {
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockPriceHistoryRepository historyRepository;
    @Mock
    private StockPriceDailyRepository dailyRepository;
    @Mock
    private StockApiClient stockApiClient;
    @Mock
    private StockHistoryBuilder stockHistoryBuilder;
    @Mock
    private StockSnapshotUpdater stockSnapshotUpdater;
    @Mock
    private MarketTimeService marketTimeService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StockServiceImpl(
                stockRepository,
                historyRepository,
                dailyRepository,
                stockApiClient,
                stockHistoryBuilder,
                stockSnapshotUpdater,
                marketTimeService
        );
    }

    @Test
    void dailyBackfillSkipsExistingDailyRowByDefault() throws Exception {
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        Stock stock = stock("MSFT");
        StockPriceDaily existing = daily("MSFT", tradingDate, "101.00");
        LocalDateTime originalCreatedAt = existing.getCreatedAt();
        when(stockApiClient.fetchDailyRange("MSFT", tradingDate, tradingDate))
                .thenReturn(dailyResponse("MSFT", tradingDate, "105.25"));
        when(stockRepository.findBySymbolIn(List.of("MSFT"))).thenReturn(List.of(stock));
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate)).thenReturn(Optional.of(existing));

        BackfillResultDto result = service.backfillDailyRange("MSFT", tradingDate, tradingDate);

        assertThat(result.savedRows()).isZero();
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(existing.getClosePrice()).isEqualByComparingTo("101.00");
        assertThat(existing.getCreatedAt()).isEqualTo(originalCreatedAt);
        verify(dailyRepository, never()).saveAll(anyList());
    }

    @Test
    void dailyBackfillInsertsMissingDailyRow() throws Exception {
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        when(stockApiClient.fetchDailyRange("MSFT", tradingDate, tradingDate))
                .thenReturn(dailyResponse("MSFT", tradingDate, "105.25"));
        when(stockRepository.findBySymbolIn(List.of("MSFT"))).thenReturn(List.of(stock("MSFT")));
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate)).thenReturn(Optional.empty());

        BackfillResultDto result = service.backfillDailyRange("MSFT", tradingDate, tradingDate);

        assertThat(result.savedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isZero();
        verify(dailyRepository).saveAll(anyList());
    }

    @Test
    void currentTradingDayRefreshRunsEvenWhenDailyRowAlreadyExists() throws Exception {
        LocalDate tradingDate = LocalDate.of(2026, 6, 15);
        StockPriceDaily existing = daily("MSFT", tradingDate, "101.00");
        when(marketTimeService.getCurrentNewYorkDate()).thenReturn(tradingDate);
        when(marketTimeService.isTradingDay(tradingDate)).thenReturn(true);
        when(stockApiClient.fetchDailyRange("MSFT", tradingDate, tradingDate))
                .thenReturn(dailyResponse("MSFT", tradingDate, "102.00"))
                .thenReturn(dailyResponse("MSFT", tradingDate, "105.00"));
        when(stockRepository.findBySymbolIn(List.of("MSFT"))).thenReturn(List.of(stock("MSFT")));
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", tradingDate)).thenReturn(Optional.of(existing));

        BackfillResultDto earlyResult = service.refreshDailyForDate("MSFT", tradingDate);
        BackfillResultDto finalResult = service.refreshDailyForDate("MSFT", tradingDate);

        assertThat(earlyResult.jobType()).isEqualTo("daily-refresh");
        assertThat(earlyResult.savedRows()).isEqualTo(1);
        assertThat(finalResult.savedRows()).isEqualTo(1);
        assertThat(existing.getClosePrice()).isEqualByComparingTo("105.00");
        verify(stockApiClient, times(2)).fetchDailyRange("MSFT", tradingDate, tradingDate);
    }

    @Test
    void currentTradingDayRefreshRejectsHistoricalDateWithoutUpdatingExistingDailyRow() throws Exception {
        LocalDate currentNewYorkDate = LocalDate.of(2026, 6, 16);
        LocalDate historicalDate = LocalDate.of(2026, 6, 12);
        when(marketTimeService.getCurrentNewYorkDate()).thenReturn(currentNewYorkDate);

        BackfillResultDto result = service.refreshDailyForDate("MSFT", historicalDate);

        assertThat(result.savedRows()).isZero();
        assertThat(result.skippedRows()).isZero();
        assertThat(result.messages()).containsExactly(
                "Daily refresh may update existing daily rows only for the current New York trading date: "
                        + currentNewYorkDate
        );
        verify(stockApiClient, never()).fetchDailyRange("MSFT", historicalDate, historicalDate);
        verify(dailyRepository, never()).findBySymbolAndTradingDate("MSFT", historicalDate);
    }

    @Test
    void missingDailyBackfillInsertsMissingRowsAndCountsExistingRowsAsSkipped() throws Exception {
        LocalDate firstDate = LocalDate.of(2026, 6, 15);
        LocalDate secondDate = LocalDate.of(2026, 6, 16);
        StockPriceDaily existing = daily("MSFT", firstDate, "101.00");
        when(marketTimeService.tradingDaysBetween(firstDate, secondDate)).thenReturn(List.of(firstDate, secondDate));
        when(dailyRepository.findExistingTradingDates("MSFT", firstDate, secondDate)).thenReturn(List.of(firstDate));
        when(stockApiClient.fetchDailyRange("MSFT", firstDate, secondDate))
                .thenReturn(dailyResponse(
                        "MSFT",
                        new DailyValue(firstDate, "102.00"),
                        new DailyValue(secondDate, "103.00")
                ));
        when(stockRepository.findBySymbolIn(List.of("MSFT"))).thenReturn(List.of(stock("MSFT")));
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", firstDate)).thenReturn(Optional.of(existing));
        when(dailyRepository.findBySymbolAndTradingDate("MSFT", secondDate)).thenReturn(Optional.empty());

        BackfillResultDto result = service.backfillMissingDaily("MSFT", firstDate, secondDate);

        assertThat(result.jobType()).isEqualTo("daily-missing");
        assertThat(result.savedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(existing.getClosePrice()).isEqualByComparingTo("101.00");
    }

    @Test
    void olderCompleteDailyRangeStillSkipsApiCall() throws Exception {
        LocalDate olderDate = LocalDate.of(2026, 6, 12);
        when(marketTimeService.tradingDaysBetween(olderDate, olderDate)).thenReturn(List.of(olderDate));
        when(dailyRepository.findExistingTradingDates("MSFT", olderDate, olderDate)).thenReturn(List.of(olderDate));

        BackfillResultDto result = service.backfillMissingDaily("MSFT", olderDate, olderDate);

        assertThat(result.savedRows()).isZero();
        assertThat(result.messages()).containsExactly("No missing daily candles detected");
        verify(stockApiClient, never()).fetchDailyRange("MSFT", olderDate, olderDate);
    }

    private ObjectNode dailyResponse(String symbol, LocalDate tradingDate, String closePrice) {
        return dailyResponse(symbol, new DailyValue(tradingDate, closePrice));
    }

    private ObjectNode dailyResponse(String symbol, DailyValue... values) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode symbolNode = root.putObject(symbol);
        var valuesNode = symbolNode.putArray("values");
        for (DailyValue dailyValue : values) {
            ObjectNode value = valuesNode.addObject();
            value.put("datetime", dailyValue.tradingDate().toString());
            value.put("open", "100.00");
            value.put("high", "106.00");
            value.put("low", "98.00");
            value.put("close", dailyValue.closePrice());
            value.put("volume", 5000L);
        }
        return root;
    }

    private Stock stock(String symbol) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setCompanyName(symbol);
        return stock;
    }

    private StockPriceDaily daily(String symbol, LocalDate tradingDate, String closePrice) {
        StockPriceDaily daily = new StockPriceDaily();
        daily.setSymbol(symbol);
        daily.setTradingDate(tradingDate);
        daily.setOpenPrice(new BigDecimal("100.00"));
        daily.setHighPrice(new BigDecimal("105.00"));
        daily.setLowPrice(new BigDecimal("99.00"));
        daily.setClosePrice(new BigDecimal(closePrice));
        daily.setVolume(4000L);
        daily.setSource("TwelveData");
        daily.setCreatedAt(LocalDateTime.of(2026, 6, 15, 16, 14));
        daily.setUpdatedAt(LocalDateTime.of(2026, 6, 15, 16, 14));
        return daily;
    }

    private record DailyValue(LocalDate tradingDate, String closePrice) {
    }
}
