package net.boyuan.stockmentor.ai.repository;

import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface StockAiSuggestionBatchRepository extends
        JpaRepository<StockAiSuggestionBatch, Long>,
        JpaSpecificationExecutor<StockAiSuggestionBatch> {
    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId,
            StockAiSuggestionBatchStatus status,
            LocalDateTime now
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId,
            Collection<StockAiSuggestionBatchStatus> statuses,
            LocalDateTime now
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByUpdatedAtDescCreatedAtDesc(
            Long userId,
            Collection<StockAiSuggestionBatchStatus> statuses,
            LocalDateTime now
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            StockAiSuggestionBatchStatus status
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            StockAiSuggestionBatchStatus status,
            LocalDateTime createdAfter
    );

    Optional<StockAiSuggestionBatch> findByUserUserIdAndModelAndPromptVersionAndInputHashAndStatus(
            Long userId,
            String model,
            String promptVersion,
            String inputHash,
            StockAiSuggestionBatchStatus status
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
            Long userId,
            String model,
            String promptVersion,
            String inputHash
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
            Long userId,
            String model,
            String promptVersion,
            String inputHash,
            Collection<StockAiSuggestionBatchStatus> statuses
    );

    Optional<StockAiSuggestionBatch> findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
            Long userId,
            StockAiSuggestionTriggerReason triggerReason
    );
}
