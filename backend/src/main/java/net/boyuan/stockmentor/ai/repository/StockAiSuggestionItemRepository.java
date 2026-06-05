package net.boyuan.stockmentor.ai.repository;

import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionItem;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockAiSuggestionItemRepository extends JpaRepository<StockAiSuggestionItem, Long> {
    List<StockAiSuggestionItem> findBySuggestionBatchAndStatusInOrderByRankNoAsc(
            StockAiSuggestionBatch suggestionBatch,
            Collection<StockAiSuggestionItemStatus> statuses
    );

    List<StockAiSuggestionItem> findBySuggestionBatchOrderByRankNoAsc(StockAiSuggestionBatch suggestionBatch);

    List<StockAiSuggestionItem> findByUserUserIdAndStatus(Long userId, StockAiSuggestionItemStatus status);

    Optional<StockAiSuggestionItem> findBySuggestionItemIdAndUserUserId(Long suggestionItemId, Long userId);
}
