package net.boyuan.stockmentor.ai.service;

import net.boyuan.stockmentor.ai.dto.admin.*;

public interface AdminAiSuggestionMonitoringService {
    AdminPageResponse<AdminAiSuggestionBatchRowResponse> listBatches(
            Long userId,
            String email,
            String status,
            String triggerReason,
            String from,
            String to,
            int page,
            int size
    );

    AdminAiSuggestionBatchDetailResponse getBatch(Long batchId);

    AdminPageResponse<AdminAiSuggestionBatchRowResponse> listFailures(
            String from,
            String to,
            String triggerReason,
            int page,
            int size
    );

    AdminAiSuggestionUsageSummaryResponse usageSummary(String from, String to);

    AdminPageResponse<AiSuggestionRefreshJobResponse> listRefreshJobs(
            String status,
            String triggeredBy,
            String from,
            String to,
            int page,
            int size
    );

    AiSuggestionRefreshJobResponse getRefreshJob(Long jobId);
}
