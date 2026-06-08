package net.boyuan.stockmentor.ai.service.impl;

import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.dto.admin.AiSuggestionRefreshJobResponse;
import net.boyuan.stockmentor.ai.entity.AiSuggestionRefreshJob;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.model.AiSuggestionRefreshTriggeredBy;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.repository.AiSuggestionRefreshJobRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAiSuggestionScheduledRefreshServiceImplTests {
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserInvestmentProfileRepository profileRepository;
    @Mock
    private StockAiSuggestionService stockAiSuggestionService;
    @Mock
    private StockAiSuggestionBatchRepository batchRepository;
    @Mock
    private AiSuggestionRefreshJobRepository jobRepository;

    private StockAiSuggestionScheduledRefreshServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StockAiSuggestionScheduledRefreshServiceImpl(
                appUserRepository,
                profileRepository,
                stockAiSuggestionService,
                batchRepository,
                jobRepository
        );
        AtomicLong jobIds = new AtomicLong(1);
        when(jobRepository.save(any(AiSuggestionRefreshJob.class))).thenAnswer(invocation -> {
            AiSuggestionRefreshJob job = invocation.getArgument(0);
            if (job.getJobId() == null) {
                job.setJobId(jobIds.getAndIncrement());
            }
            return job;
        });
    }

    @Test
    void adminManualRunCreatesSuccessJobAndBypassesManualCooldown() {
        AppUser user = user(1L);
        AppUser admin = admin();
        when(appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(eq(AppUserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L))
                .thenReturn(Optional.of(new UserInvestmentProfile()));
        when(stockAiSuggestionService.generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false))
                .thenReturn(response(10L, "SUCCESS"));
        when(batchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, user, StockAiSuggestionBatchStatus.SUCCESS, LocalDateTime.now().plusSeconds(1))));

        AiSuggestionRefreshJobResponse result = service.runScheduledRefresh(AiSuggestionRefreshTriggeredBy.ADMIN_MANUAL, admin);

        assertEquals("SUCCESS", result.status());
        assertEquals("ADMIN_MANUAL", result.triggeredBy());
        assertEquals(99L, result.triggeredByUserId());
        assertEquals(1, result.processedUsers());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failedCount());
        verify(stockAiSuggestionService).generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false);
    }

    @Test
    void missingProfileIncrementsSkippedAndStillSucceeds() {
        AppUser user = user(1L);
        when(appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(eq(AppUserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        AiSuggestionRefreshJobResponse result = service.runScheduledRefresh(AiSuggestionRefreshTriggeredBy.SCHEDULED, null);

        assertEquals("SUCCESS", result.status());
        assertEquals(1, result.processedUsers());
        assertEquals(1, result.skippedUsers());
        verifyNoInteractions(stockAiSuggestionService);
    }

    @Test
    void fallbackAndReusedResponsesAreCountedSeparately() {
        AppUser fallbackUser = user(1L);
        AppUser reusedUser = user(2L);
        when(appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(eq(AppUserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fallbackUser, reusedUser)));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(anyLong()))
                .thenReturn(Optional.of(new UserInvestmentProfile()));
        when(stockAiSuggestionService.generateSuggestionsForUser(fallbackUser, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false))
                .thenReturn(response(11L, "FALLBACK_RULE_BASED"));
        when(stockAiSuggestionService.generateSuggestionsForUser(reusedUser, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false))
                .thenReturn(response(12L, "SUCCESS"));
        when(batchRepository.findById(11L)).thenReturn(Optional.of(batch(11L, fallbackUser, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, LocalDateTime.now())));
        when(batchRepository.findById(12L)).thenReturn(Optional.of(batch(12L, reusedUser, StockAiSuggestionBatchStatus.SUCCESS, LocalDateTime.now().minusHours(2))));

        AiSuggestionRefreshJobResponse result = service.runScheduledRefresh(AiSuggestionRefreshTriggeredBy.SCHEDULED, null);

        assertEquals("SUCCESS", result.status());
        assertEquals(2, result.processedUsers());
        assertEquals(1, result.fallbackCount());
        assertEquals(1, result.reusedCount());
        assertEquals(0, result.failedCount());
    }

    @Test
    void oneUserFailureDoesNotStopJobAndMarksPartialSuccess() {
        AppUser failedUser = user(1L);
        AppUser successUser = user(2L);
        when(appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(eq(AppUserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedUser, successUser)));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(anyLong()))
                .thenReturn(Optional.of(new UserInvestmentProfile()));
        when(stockAiSuggestionService.generateSuggestionsForUser(failedUser, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false))
                .thenThrow(new IllegalStateException("boom"));
        when(stockAiSuggestionService.generateSuggestionsForUser(successUser, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false))
                .thenReturn(response(20L, "SUCCESS"));
        when(batchRepository.findById(20L)).thenReturn(Optional.of(batch(20L, successUser, StockAiSuggestionBatchStatus.SUCCESS, LocalDateTime.now().plusSeconds(1))));

        AiSuggestionRefreshJobResponse result = service.runScheduledRefresh(AiSuggestionRefreshTriggeredBy.SCHEDULED, null);

        assertEquals("PARTIAL_SUCCESS", result.status());
        assertEquals(2, result.processedUsers());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failedCount());
    }

    private AppUser user(Long userId) {
        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setEmail("user" + userId + "@example.com");
        user.setUsername("user" + userId);
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);
        user.setOnboardingCompleted(true);
        return user;
    }

    private AppUser admin() {
        AppUser admin = user(99L);
        admin.setRole(AppUserRole.ADMIN);
        admin.setEmail("admin@example.com");
        admin.setUsername("admin");
        return admin;
    }

    private StockAiSuggestionResponse response(Long batchId, String status) {
        return new StockAiSuggestionResponse(
                1L,
                batchId,
                status,
                "SCHEDULED_REFRESH",
                "summary",
                "7D",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24),
                status.startsWith("FALLBACK"),
                true,
                null,
                List.of(),
                List.of(),
                "message"
        );
    }

    private StockAiSuggestionBatch batch(
            Long batchId,
            AppUser user,
            StockAiSuggestionBatchStatus status,
            LocalDateTime createdAt
    ) {
        StockAiSuggestionBatch batch = new StockAiSuggestionBatch();
        batch.setSuggestionBatchId(batchId);
        batch.setUser(user);
        batch.setStatus(status);
        batch.setTriggerReason(StockAiSuggestionTriggerReason.SCHEDULED_REFRESH);
        batch.setCreatedAt(createdAt);
        return batch;
    }
}
