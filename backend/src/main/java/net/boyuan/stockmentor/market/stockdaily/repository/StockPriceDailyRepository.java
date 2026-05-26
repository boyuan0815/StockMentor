package net.boyuan.stockmentor.market.stockdaily.repository;

import net.boyuan.stockmentor.market.stockdaily.entity.StockPriceDaily;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockPriceDailyRepository extends JpaRepository<StockPriceDaily, Long> {
    boolean existsBySymbolAndTradingDate(String symbol, LocalDate tradingDate);

    List<StockPriceDaily> findBySymbolAndTradingDateBetween(
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    );

    List<StockPriceDaily> findByTradingDateBefore(LocalDate cutoffDate);

    Optional<StockPriceDaily> findTopBySymbolOrderByTradingDateDesc(String symbol);

    @Query("""
        select d.tradingDate
        from StockPriceDaily d
        where d.symbol = :symbol
        and d.tradingDate between :startDate and :endDate
    """)
    List<LocalDate> findExistingTradingDates(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        select d
        from StockPriceDaily d
        where d.symbol = :symbol
        order by d.tradingDate desc
    """)
    List<StockPriceDaily> findLatestBySymbol(
            @Param("symbol") String symbol,
            Pageable pageable
    );

    List<StockPriceDaily> findBySymbolInAndTradingDateBetween(
            Collection<String> symbols,
            LocalDate startDate,
            LocalDate endDate
    );
}
