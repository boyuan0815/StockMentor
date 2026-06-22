package net.boyuan.stockmentor.market.stockpricehistory.repository;

import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

//    every table must put alias because it is a standard practice
//    JPQL must use the EXACT CLASSNAME: StockPriceHistory
//    JPQL DOESN'T use classname in lower case: stockpricehistory
//    JPQL DOESN'T use database table name: stock_price_history_1min
    @Query("""
        select h.timestamp
        from StockPriceHistory h
        where h.symbol = :symbol
        and h.timeInterval = :timeInterval
        and h.timestamp between :start and :end
    """)
    List<LocalDateTime> findExistingTimestamps(
           @Param("symbol") String symbol,
           @Param("timeInterval") String timeInterval,
           @Param("start") LocalDateTime start,
           @Param("end") LocalDateTime end
    );

    long countBySymbolAndTimestampBetween(
            String symbol,
            LocalDateTime start,
            LocalDateTime end
    );

    List<StockPriceHistory> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol,
            LocalDateTime start,
            LocalDateTime end
    );

    List<StockPriceHistory> findBySymbolAndTradingDateOrderByTimestampAsc(
            String symbol,
            LocalDate tradingDate
    );

    List<StockPriceHistory> findBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampAsc(
            String symbol,
            LocalDate tradingDate,
            String timeInterval,
            LocalDateTime timestamp
    );

    Optional<StockPriceHistory> findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualOrderByTimestampDesc(
            String symbol,
            LocalDate tradingDate,
            String timeInterval,
            LocalDateTime timestamp
    );

    Optional<StockPriceHistory> findTopBySymbolAndTradingDateAndTimeIntervalAndTimestampLessThanEqualAndClosePriceGreaterThanOrderByTimestampDesc(
            String symbol,
            LocalDate tradingDate,
            String timeInterval,
            LocalDateTime timestamp,
            BigDecimal closePrice
    );

    Optional<StockPriceHistory> findTopBySymbolAndTradingDateAndTimeIntervalOrderByTimestampAsc(
            String symbol,
            LocalDate tradingDate,
            String timeInterval
    );

    @Query("""
        select max(h.highPrice) as highPrice,
               min(h.lowPrice) as lowPrice
        from StockPriceHistory h
        where h.symbol = :symbol
        and h.tradingDate = :tradingDate
        and h.timeInterval = :timeInterval
        and h.timestamp <= :timestamp
    """)
    IntradayDayRangeProjection findDayRangeAtOrBefore(
            @Param("symbol") String symbol,
            @Param("tradingDate") LocalDate tradingDate,
            @Param("timeInterval") String timeInterval,
            @Param("timestamp") LocalDateTime timestamp
    );

    @Query("""
        select coalesce(sum(h.volume), 0)
        from StockPriceHistory h
        where h.symbol = :symbol
        and h.tradingDate = :tradingDate
        and h.timeInterval = :timeInterval
        and h.timestamp <= :timestamp
    """)
    Long sumVolumeAtOrBefore(
            @Param("symbol") String symbol,
            @Param("tradingDate") LocalDate tradingDate,
            @Param("timeInterval") String timeInterval,
            @Param("timestamp") LocalDateTime timestamp
    );

    @Query("""
        select max(h.tradingDate)
        from StockPriceHistory h
        where h.symbol = :symbol
        and h.tradingDate is not null
    """)
    Optional<LocalDate> findLatestTradingDateBySymbol(@Param("symbol") String symbol);

    Optional<StockPriceHistory> findTopBySymbolOrderByTimestampDesc(String symbol);

    @Modifying
    long deleteBySymbolAndTimestampBetween(
            String symbol,
            LocalDateTime start,
            LocalDateTime end
    );

    @Modifying
    long deleteBySymbolAndTradingDate(
            String symbol,
            LocalDate tradingDate
    );

    @Query("""
        select h.symbol as symbol,
               h.tradingDate as tradingDate,
               count(h) as rowCount
        from StockPriceHistory h
        left join StockPriceDaily d
            on d.symbol = h.symbol
            and d.tradingDate = h.tradingDate
        where h.timestamp < :cutoffTimestamp
        and h.tradingDate is not null
        and d.dailyId is null
        group by h.symbol, h.tradingDate
    """)
    List<SkippedIntradayCleanupRow> findSkippedCleanupRows(
            @Param("cutoffTimestamp") LocalDateTime cutoffTimestamp
    );

}
