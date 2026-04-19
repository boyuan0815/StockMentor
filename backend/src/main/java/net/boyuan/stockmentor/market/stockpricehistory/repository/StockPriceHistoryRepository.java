package net.boyuan.stockmentor.market.stockpricehistory.repository;

import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

//    every table must put alias because it is a standard practice
//    JPQL must use the EXACT CLASSNAME: StockPriceHistory
//    JPQL DOESN'T use classname in lower case: stockpricehistory
//    JPQL DOESN'T use database table name: stock_price_history
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

}
