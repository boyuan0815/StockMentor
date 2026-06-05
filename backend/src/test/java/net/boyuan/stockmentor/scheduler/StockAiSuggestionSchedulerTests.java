package net.boyuan.stockmentor.scheduler;

import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.common.util.MarketTimeService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAiSuggestionSchedulerTests {
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserInvestmentProfileRepository profileRepository;
    @Mock
    private StockAiSuggestionService stockAiSuggestionService;
    @Mock
    private MarketTimeService marketTimeService;

    private StockAiSuggestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StockAiSuggestionScheduler(appUserRepository, profileRepository, stockAiSuggestionService, marketTimeService);
    }

    @Test
    void scheduledRefreshProcessesEligibleUsersAndSkipsMissingProfile() {
        AppUser withProfile = user(1L);
        AppUser missingProfile = user(2L);
        when(appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(eq(AppUserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withProfile, missingProfile)));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(new UserInvestmentProfile()));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(2L)).thenReturn(Optional.empty());
        when(stockAiSuggestionService.generateSuggestionsForUser(eq(withProfile), eq(StockAiSuggestionTriggerReason.SCHEDULED_REFRESH), eq(false)))
                .thenReturn(response(10L, "Generated new AI stock suggestions", false));

        StockAiSuggestionScheduler.ScheduledRefreshSummary summary = scheduler.runScheduledRefreshForEligibleUsers();

        assertEquals(2, summary.processedUsers());
        assertEquals(1, summary.generated());
        assertEquals(1, summary.skippedMissingProfile());
        verify(stockAiSuggestionService, never()).generateSuggestionsForUser(eq(missingProfile), any(), anyBoolean());
    }

    @Test
    void scheduledRefreshContinuesAfterOneUserFailure() {
        AppUser failedUser = user(1L);
        AppUser successUser = user(2L);
        when(appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(eq(AppUserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedUser, successUser)));
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(anyLong())).thenReturn(Optional.of(new UserInvestmentProfile()));
        when(stockAiSuggestionService.generateSuggestionsForUser(eq(failedUser), eq(StockAiSuggestionTriggerReason.SCHEDULED_REFRESH), eq(false)))
                .thenThrow(new IllegalStateException("boom"));
        when(stockAiSuggestionService.generateSuggestionsForUser(eq(successUser), eq(StockAiSuggestionTriggerReason.SCHEDULED_REFRESH), eq(false)))
                .thenReturn(response(10L, "Returned existing suggestions because your profile and stock data are unchanged.", false));

        StockAiSuggestionScheduler.ScheduledRefreshSummary summary = scheduler.runScheduledRefreshForEligibleUsers();

        assertEquals(2, summary.processedUsers());
        assertEquals(1, summary.failed());
        assertEquals(1, summary.reusedOrSkippedUnchanged());
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

    private StockAiSuggestionResponse response(Long batchId, String message, boolean fallbackUsed) {
        return new StockAiSuggestionResponse(
                1L,
                batchId,
                fallbackUsed ? "FALLBACK_RULE_BASED" : "SUCCESS",
                "SCHEDULED_REFRESH",
                "summary",
                "7D",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24),
                fallbackUsed,
                true,
                null,
                List.of(),
                List.of(),
                message
        );
    }
}
