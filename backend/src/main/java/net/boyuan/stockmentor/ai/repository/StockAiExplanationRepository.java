package net.boyuan.stockmentor.ai.repository;

import net.boyuan.stockmentor.ai.entity.StockAiExplanation;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAiExplanationRepository extends JpaRepository<StockAiExplanation, Long> {
    Optional<StockAiExplanation> findByAnalysisSnapshotAndModelAndPromptVersion(
            StockAnalysisSnapshot analysisSnapshot,
            String model,
            String promptVersion
    );

    boolean existsByAnalysisSnapshotAndModelAndPromptVersion(
            StockAnalysisSnapshot analysisSnapshot,
            String model,
            String promptVersion
    );

    Optional<StockAiExplanation> findTopBySymbolAndTimeframeOrderByCreatedAtDesc(
            String symbol,
            String timeframe
    );
}
