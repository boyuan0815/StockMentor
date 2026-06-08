package net.boyuan.stockmentor.ai.repository;

import net.boyuan.stockmentor.ai.entity.AiSuggestionRefreshJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AiSuggestionRefreshJobRepository extends
        JpaRepository<AiSuggestionRefreshJob, Long>,
        JpaSpecificationExecutor<AiSuggestionRefreshJob> {
}
