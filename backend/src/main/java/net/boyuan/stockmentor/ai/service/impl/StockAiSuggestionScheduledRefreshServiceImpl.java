package net.boyuan.stockmentor.ai.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.dto.admin.AiSuggestionRefreshJobResponse;
import net.boyuan.stockmentor.ai.entity.AiSuggestionRefreshJob;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshJobStatus;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.repository.AiSuggestionRefreshJobRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionScheduledRefreshService;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StockAiSuggestionScheduledRefreshServiceImpl implements StockAiSuggestionScheduledRefreshService {
    private static final Logger log = LoggerFactory.getLogger(StockAiSuggestionScheduledRefreshServiceImpl.class);
    private static final int PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final StockAiSuggestionService stockAiSuggestionService;
    private final StockAiSuggestionBatchRepository batchRepository;
    private final AiSuggestionRefreshJobRepository jobRepository;

    @Override
    public AiSuggestionRefreshJobResponse runScheduledRefresh(AiSuggestionRefreshTriggeredBy triggeredBy, AppUser adminUserOrNull) {
        LocalDateTime startedAt = LocalDateTime.now();
        AiSuggestionRefreshJob job = createRunningJob(triggeredBy, adminUserOrNull, startedAt);
        log.info("AI suggestion refresh job started jobId={} triggeredBy={} adminUserId={}",
                job.getJobId(), triggeredBy, adminUserOrNull == null ? null : adminUserOrNull.getUserId());

        JobCounters counters = new JobCounters();
        try {
            int pageNumber = 0;
            Page<AppUser> page;
            do {
                page = appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(
                        AppUserStatus.ACTIVE,
                        PageRequest.of(pageNumber, PAGE_SIZE)
                );

                for (AppUser user : page.getContent()) {
                    counters.processedUsers++;
                    processUser(user, startedAt, counters);
                }

                pageNumber++;
            } while (page.hasNext());

            finishSuccessfulJob(job, counters);
            log.info("AI suggestion refresh job finished jobId={} status={} processed={} skipped={} success={} reused={} fallback={} failed={}",
                    job.getJobId(),
                    job.getStatus(),
                    job.getProcessedUsers(),
                    job.getSkippedUsers(),
                    job.getSuccessCount(),
                    job.getReusedCount(),
                    job.getFallbackCount(),
                    job.getFailedCount());
            return toJobResponse(job);
        } catch (RuntimeException e) {
            log.warn("AI suggestion refresh job setup failed jobId={}", job.getJobId(), e);
            markFailed(job, counters, "AI suggestion refresh job failed: " + safeMessage(e));
            return toJobResponse(job);
        }
    }

    private void processUser(AppUser user, LocalDateTime jobStartedAt, JobCounters counters) {
        try {
            if (profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId()).isEmpty()) {
                counters.skippedUsers++;
                log.info("AI suggestion refresh skipped userId={} because no investment profile exists", user.getUserId());
                return;
            }

            StockAiSuggestionResponse response = stockAiSuggestionService.generateSuggestionsForUser(
                    user,
                    StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                    false
            );
            classifyResponse(response, jobStartedAt, counters);
        } catch (RuntimeException e) {
            counters.failedCount++;
            log.warn("AI suggestion refresh failed for userId={}", user.getUserId(), e);
        }
    }

    private void classifyResponse(StockAiSuggestionResponse response, LocalDateTime jobStartedAt, JobCounters counters) {
        if (response == null || response.batchId() == null) {
            counters.skippedUsers++;
            return;
        }

        StockAiSuggestionBatch batch = batchRepository.findById(response.batchId()).orElse(null);
        if (batch == null) {
            classifyByResponseStatus(response, counters);
            return;
        }

        if (isFallback(batch.getStatus())) {
            counters.fallbackCount++;
            return;
        }
        if (batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS) {
            if (batch.getCreatedAt() != null && !batch.getCreatedAt().isBefore(jobStartedAt)) {
                counters.successCount++;
            } else {
                counters.reusedCount++;
            }
            return;
        }
        if (batch.getStatus() == StockAiSuggestionBatchStatus.FAILED) {
            counters.failedCount++;
        } else {
            counters.skippedUsers++;
        }
    }

    private void classifyByResponseStatus(StockAiSuggestionResponse response, JobCounters counters) {
        if (response.batchStatus() == null) {
            counters.skippedUsers++;
            return;
        }
        try {
            StockAiSuggestionBatchStatus status = StockAiSuggestionBatchStatus.valueOf(response.batchStatus());
            if (isFallback(status)) {
                counters.fallbackCount++;
            } else if (status == StockAiSuggestionBatchStatus.SUCCESS) {
                counters.successCount++;
            } else if (status == StockAiSuggestionBatchStatus.FAILED) {
                counters.failedCount++;
            } else {
                counters.skippedUsers++;
            }
        } catch (IllegalArgumentException e) {
            counters.skippedUsers++;
        }
    }

    private AiSuggestionRefreshJob createRunningJob(
            AiSuggestionRefreshTriggeredBy triggeredBy,
            AppUser adminUserOrNull,
            LocalDateTime startedAt
    ) {
        AiSuggestionRefreshJob job = new AiSuggestionRefreshJob();
        job.setTriggeredBy(triggeredBy);
        job.setTriggeredByUser(adminUserOrNull);
        job.setStartedAt(startedAt);
        job.setProcessedUsers(0);
        job.setSkippedUsers(0);
        job.setSuccessCount(0);
        job.setReusedCount(0);
        job.setFallbackCount(0);
        job.setFailedCount(0);
        job.setStatus(AiSuggestionRefreshJobStatus.RUNNING);
        job.setMessage("AI suggestion refresh job is running");
        job.setCreatedAt(startedAt);
        job.setUpdatedAt(startedAt);
        return jobRepository.save(job);
    }

    private void finishSuccessfulJob(AiSuggestionRefreshJob job, JobCounters counters) {
        LocalDateTime finishedAt = LocalDateTime.now();
        job.setFinishedAt(finishedAt);
        applyCounters(job, counters);
        job.setStatus(counters.failedCount > 0 ? AiSuggestionRefreshJobStatus.PARTIAL_SUCCESS : AiSuggestionRefreshJobStatus.SUCCESS);
        job.setMessage(counters.failedCount > 0
                ? "AI suggestion refresh completed with some user failures"
                : "AI suggestion refresh completed successfully");
        job.setUpdatedAt(finishedAt);
        jobRepository.save(job);
    }

    private void markFailed(AiSuggestionRefreshJob job, JobCounters counters, String message) {
        LocalDateTime finishedAt = LocalDateTime.now();
        job.setFinishedAt(finishedAt);
        applyCounters(job, counters);
        job.setStatus(AiSuggestionRefreshJobStatus.FAILED);
        job.setMessage(message);
        job.setUpdatedAt(finishedAt);
        jobRepository.save(job);
    }

    private void applyCounters(AiSuggestionRefreshJob job, JobCounters counters) {
        job.setProcessedUsers(counters.processedUsers);
        job.setSkippedUsers(counters.skippedUsers);
        job.setSuccessCount(counters.successCount);
        job.setReusedCount(counters.reusedCount);
        job.setFallbackCount(counters.fallbackCount);
        job.setFailedCount(counters.failedCount);
    }

    private boolean isFallback(StockAiSuggestionBatchStatus status) {
        return status == StockAiSuggestionBatchStatus.FALLBACK_CACHED
                || status == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED;
    }

    private String safeMessage(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private AiSuggestionRefreshJobResponse toJobResponse(AiSuggestionRefreshJob job) {
        return new AiSuggestionRefreshJobResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getTriggeredBy().name(),
                job.getTriggeredByUser() == null ? null : job.getTriggeredByUser().getUserId(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getProcessedUsers(),
                job.getSkippedUsers(),
                job.getSuccessCount(),
                job.getReusedCount(),
                job.getFallbackCount(),
                job.getFailedCount(),
                job.getMessage()
        );
    }

    private static class JobCounters {
        private int processedUsers;
        private int skippedUsers;
        private int successCount;
        private int reusedCount;
        private int fallbackCount;
        private int failedCount;
    }
}
