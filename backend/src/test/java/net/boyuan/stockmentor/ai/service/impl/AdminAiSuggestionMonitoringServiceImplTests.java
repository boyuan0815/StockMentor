package net.boyuan.stockmentor.ai.service.impl;

import net.boyuan.stockmentor.ai.dto.admin.AdminAiSuggestionBatchDetailResponse;
import net.boyuan.stockmentor.ai.dto.admin.AdminAiSuggestionUsageSummaryResponse;
import net.boyuan.stockmentor.ai.entity.AiSuggestionRefreshJob;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionItem;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshJobStatus;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionItemStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.repository.AiSuggestionRefreshJobRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAiSuggestionMonitoringServiceImplTests {
    @Mock
    private StockAiSuggestionBatchRepository batchRepository;
    @Mock
    private StockAiSuggestionItemRepository itemRepository;
    @Mock
    private AiSuggestionRefreshJobRepository jobRepository;

    private AdminAiSuggestionMonitoringServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiSuggestionMonitoringServiceImpl(batchRepository, itemRepository, jobRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usageSummaryCountsStatusesTriggersAndNullTokensAsZero() {
        StockAiSuggestionBatch success = batch(1L, StockAiSuggestionBatchStatus.SUCCESS, StockAiSuggestionTriggerReason.MANUAL_REFRESH);
        success.setPromptTokens(10);
        success.setCompletionTokens(null);
        success.setTotalTokens(30);
        StockAiSuggestionBatch failed = batch(2L, StockAiSuggestionBatchStatus.FAILED, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH);
        StockAiSuggestionBatch fallback = batch(3L, StockAiSuggestionBatchStatus.FALLBACK_CACHED, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH);
        fallback.setPromptTokens(5);
        fallback.setCompletionTokens(7);
        fallback.setTotalTokens(null);

        when(batchRepository.findAll(any(Specification.class))).thenReturn(List.of(success, failed, fallback));

        AdminAiSuggestionUsageSummaryResponse response = service.usageSummary(null, null);

        assertEquals(3L, response.totalBatches());
        assertEquals(1L, response.successCount());
        assertEquals(1L, response.failedCount());
        assertEquals(1L, response.fallbackCachedCount());
        assertEquals(0L, response.fallbackRuleBasedCount());
        assertEquals(15L, response.totalPromptTokens());
        assertEquals(7L, response.totalCompletionTokens());
        assertEquals(30L, response.totalTokens());
        assertTrue(response.groupedByTriggerReason().stream().anyMatch(row -> row.key().equals("SCHEDULED_REFRESH") && row.count() == 2L));
        assertTrue(response.groupedByStatus().stream().anyMatch(row -> row.key().equals("SUCCESS") && row.count() == 1L));
    }

    @Test
    void batchDetailReturnsItemsAndAdminFields() {
        StockAiSuggestionBatch batch = batch(10L, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, StockAiSuggestionTriggerReason.MANUAL_REFRESH);
        batch.setInputHash("abc123");
        batch.setProfileVersion(4);
        StockAiSuggestionItem item = item(batch, 20L, "MSFT", 99L);
        when(batchRepository.findById(10L)).thenReturn(Optional.of(batch));
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(batch)).thenReturn(List.of(item));

        AdminAiSuggestionBatchDetailResponse response = service.getBatch(10L);

        assertEquals(10L, response.batchId());
        assertEquals("user@example.com", response.userEmail());
        assertEquals("abc123", response.inputHash());
        assertTrue(response.fallbackUsed());
        assertEquals(List.of("MSFT"), response.suggestedSymbols());
        assertEquals(1, response.itemCount());
        assertEquals(20L, response.items().get(0).itemId());
        assertEquals(99L, response.items().get(0).snapshotId());
    }

    @Test
    void invalidBatchStatusFilterReturnsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.listBatches(null, null, "not-real", null, null, null, 0, 20)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void refreshJobDetailMapsSummaryFields() {
        AiSuggestionRefreshJob job = new AiSuggestionRefreshJob();
        job.setJobId(5L);
        job.setStatus(AiSuggestionRefreshJobStatus.PARTIAL_SUCCESS);
        job.setTriggeredBy(AiSuggestionRefreshTriggeredBy.ADMIN_MANUAL);
        job.setTriggeredByUser(user());
        job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        job.setProcessedUsers(3);
        job.setSkippedUsers(1);
        job.setSuccessCount(1);
        job.setReusedCount(0);
        job.setFallbackCount(0);
        job.setFailedCount(1);
        job.setMessage("done");
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));

        var response = service.getRefreshJob(5L);

        assertEquals(5L, response.jobId());
        assertEquals("PARTIAL_SUCCESS", response.status());
        assertEquals("ADMIN_MANUAL", response.triggeredBy());
        assertEquals(1L, response.triggeredByUserId());
        assertEquals(3, response.processedUsers());
    }

    private StockAiSuggestionBatch batch(
            Long batchId,
            StockAiSuggestionBatchStatus status,
            StockAiSuggestionTriggerReason triggerReason
    ) {
        StockAiSuggestionBatch batch = new StockAiSuggestionBatch();
        batch.setSuggestionBatchId(batchId);
        batch.setUser(user());
        batch.setStatus(status);
        batch.setTriggerReason(triggerReason);
        batch.setAnalysisTimeframe("7D");
        batch.setModel("gpt-4o-mini");
        batch.setPromptVersion("stock-suggestion-v2");
        batch.setBatchSummary("fallback");
        batch.setFinishReason("stop");
        batch.setErrorMessage("error");
        batch.setCreatedAt(LocalDateTime.now());
        batch.setExpiresAt(LocalDateTime.now().plusHours(24));
        return batch;
    }

    private StockAiSuggestionItem item(StockAiSuggestionBatch batch, Long itemId, String symbol, Long snapshotId) {
        StockAnalysisSnapshot snapshot = new StockAnalysisSnapshot();
        snapshot.setAnalysisSnapshotId(snapshotId);

        StockAiSuggestionItem item = new StockAiSuggestionItem();
        item.setSuggestionBatch(batch);
        item.setSuggestionItemId(itemId);
        item.setSymbol(symbol);
        item.setRankNo(1);
        item.setMatchScore(88);
        item.setRiskLevel("moderate");
        item.setSuggestionLabel("Profile-aligned learning pick");
        item.setShortReason("Short reason.");
        item.setStatus(StockAiSuggestionItemStatus.ACTIVE);
        item.setAnalysisSnapshot(snapshot);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setUserId(1L);
        user.setEmail("user@example.com");
        user.setUsername("user");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);
        return user;
    }
}
