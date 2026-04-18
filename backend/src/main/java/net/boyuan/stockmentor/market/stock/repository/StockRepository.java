package net.boyuan.stockmentor.market.stock.repository;

import net.boyuan.stockmentor.market.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findBySymbolIn(Collection<String> symbols);
}
