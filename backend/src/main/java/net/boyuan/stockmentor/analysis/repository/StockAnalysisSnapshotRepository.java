package net.boyuan.stockmentor.analysis.repository;

import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockAnalysisSnapshotRepository extends JpaRepository<StockAnalysisSnapshot, Long> {
    Optional<StockAnalysisSnapshot> findBySymbolAndTimeframeAndSnapshotHash(
            String symbol,
            String timeframe,
            String snapshotHash
    );

    Optional<StockAnalysisSnapshot> findTopBySymbolAndTimeframeOrderByCreatedAtDesc(
            String symbol,
            String timeframe
    );

    Optional<StockAnalysisSnapshot> findTopBySymbolAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
            String symbol,
            String timeframe
    );

    List<StockAnalysisSnapshot> findBySymbolInAndTimeframeOrderByCreatedAtDesc(
            Collection<String> symbols,
            String timeframe
    );

    List<StockAnalysisSnapshot> findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(
            Collection<String> symbols,
            String timeframe
    );
}
