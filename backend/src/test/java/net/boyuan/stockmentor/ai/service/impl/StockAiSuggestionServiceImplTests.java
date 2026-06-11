package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.boyuan.stockmentor.ai.dto.OpenAiSuggestionResult;
import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionItem;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionItemStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.ai.service.OpenAiClient;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.*;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAiSuggestionServiceImplTests {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserInvestmentProfileRepository profileRepository;
    @Mock
    private StockAiSuggestionBatchRepository batchRepository;
    @Mock
    private StockAiSuggestionItemRepository itemRepository;
    @Mock
    private UserWatchlistRepository watchlistRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockAnalysisSnapshotRepository snapshotRepository;
    @Mock
    private StockAnalysisService stockAnalysisService;
    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private UserBehaviorProfileService behaviorProfileService;

    private StockAiSuggestionServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new StockAiSuggestionServiceImpl(
                currentUserService,
                profileRepository,
                batchRepository,
                itemRepository,
                watchlistRepository,
                stockRepository,
                snapshotRepository,
                stockAnalysisService,
                openAiClient,
                behaviorProfileService,
                new ObjectMapper().findAndRegisterModules()
        );

        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("beginner@example.com");
        user.setUsername("beginner");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);

        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of());
        lenient().when(watchlistRepository.findByUserUserIdAndSymbolIn(eq(1L), anyCollection())).thenReturn(List.of());
        lenient().when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDesc(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                        eq(1L),
                        eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)
                ))
                .thenReturn(Optional.empty());
        lenient().when(batchRepository.findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(1L),
                        eq(StockAiSuggestionBatchStatus.SUCCESS),
                        any()
                ))
                .thenReturn(Optional.empty());
        lenient().when(batchRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                        eq(1L),
                        eq(StockAiSuggestionBatchStatus.SUCCESS)
                ))
                .thenReturn(Optional.empty());
        lenient().when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(behaviorSummary(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void getSuggestionsDoesNotCallOpenAiWhenNoStoredBatchExists() {
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());

        StockAiSuggestionResponse response = service.getSuggestionsForCurrentUser();

        assertTrue(response.suggestedStocks().isEmpty());
        assertEquals(8, response.remainingStocks().size());
        assertTrue(response.refreshAllowed());
        assertNull(response.nextRefreshAllowedAt());
        assertNotNull(response.message());
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
        verify(behaviorProfileService, never()).getBehaviorSummaryForSuggestion(anyLong());
        verify(batchRepository, never()).save(any(StockAiSuggestionBatch.class));
        verify(batchRepository, never()).findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION)
        );
    }

    @Test
    void getSuggestionsReportsManualRefreshCooldownFromLatestManualBatch() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch storedBatch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        LocalDateTime manualCreatedAt = LocalDateTime.now().minusMinutes(10);
        storedBatch.setCreatedAt(manualCreatedAt);
        storedBatch.setExpiresAt(LocalDateTime.now().plusHours(24));

        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.of(storedBatch));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                        eq(1L),
                        eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)
                ))
                .thenReturn(Optional.of(storedBatch));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(storedBatch), anyCollection()))
                .thenReturn(List.of());

        StockAiSuggestionResponse response = service.getSuggestionsForCurrentUser();

        assertFalse(response.refreshAllowed());
        assertEquals(manualCreatedAt.plusHours(1), response.nextRefreshAllowedAt());
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
        verify(behaviorProfileService, never()).getBehaviorSummaryForSuggestion(anyLong());
        verify(batchRepository, never()).save(any(StockAiSuggestionBatch.class));
        verify(batchRepository, never()).findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionTriggerReason.NO_ACTIVE_SUGGESTION)
        );
    }

    @Test
    void refreshReusesExistingBatchWhenInputHashAlreadyExists() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingBatch = batch(profile, StockAiSuggestionBatchStatus.SUCCESS);
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.of(existingBatch));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingBatch), anyCollection()))
                .thenReturn(List.of());

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(existingBatch.getSuggestionBatchId(), response.batchId());
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
    }

    @Test
    void missingOnboardingProfileReturnsEmptyResponseWithoutOpenAiOrFallback() {
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertNull(response.batchId());
        assertTrue(response.suggestedStocks().isEmpty());
        assertTrue(response.message().contains("complete onboarding"));
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
        verify(stockAnalysisService, never()).createOrReuseSnapshot(anyString(), anyString());
        verify(behaviorProfileService, never()).getBehaviorSummaryForSuggestion(anyLong());
        verify(batchRepository, never()).save(any(StockAiSuggestionBatch.class));
    }

    @Test
    void refreshStoresRuleBasedFallbackWhenOpenAiFailsAndNoCacheExists() {
        UserInvestmentProfile profile = profile();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        StockAiSuggestionItem previousActiveItem = new StockAiSuggestionItem();
        previousActiveItem.setStatus(StockAiSuggestionItemStatus.ACTIVE);
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE))
                .thenReturn(List.of(previousActiveItem));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(10L);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() != null && item.getSymbol() != null) {
                item.setSuggestionItemId((long) savedItems.size() + 20L);
                savedItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems);

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        assertFalse(response.suggestedStocks().isEmpty());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, previousActiveItem.getStatus());
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
                        && "OpenAI unavailable".equals(batch.getErrorMessage())
        ));
        verify(itemRepository).save(previousActiveItem);
        verify(behaviorProfileService).getBehaviorSummaryForSuggestion(1L);
        verifyNoMoreInteractions(behaviorProfileService);
    }

    @Test
    void refreshStoresSuccessWhenOpenAiReturnsValidSuggestions() {
        UserInvestmentProfile profile = profile();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(new OpenAiSuggestionResult(
                true,
                validSuggestionJson(),
                100,
                80,
                180,
                "stop",
                null
        ));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(11L);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            item.setSuggestionItemId((long) savedItems.size() + 30L);
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems);

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals("SUCCESS", response.batchStatus());
        assertFalse(response.fallbackUsed());
        assertEquals(1, response.suggestedStocks().size());
        assertEquals(30L, response.suggestedStocks().get(0).itemId());
        assertEquals("MSFT", response.suggestedStocks().get(0).symbol());
        assertEquals(1, response.suggestedStocks().get(0).rankNo());
        assertEquals(76, response.suggestedStocks().get(0).matchScore());
        assertEquals("moderate", response.suggestedStocks().get(0).riskLevel());
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS
                        && batch.getErrorMessage() == null
                && Integer.valueOf(180).equals(batch.getTotalTokens())
        ));
    }

    @Test
    void rejectsSteadyMovementWhenPriceConsistencyIsChoppy() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        googSnapshot.setTrend("volatile downtrend");
        googSnapshot.setPriceConsistency("choppy downward movement");
        String invalidJson = googSuggestionJson(
                "Observing steady movement",
                "GOOG appears steady with moderate risk and complete data.",
                "GOOG shows steady movement with moderate risk, moderate volatility, stable volume, smooth consistency, and complete data for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                OpenAiSuggestionResult.failure("retry unavailable")
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        assertTrue(promptCaptor.getAllValues().get(1).contains("AI wording contradicts volatile or choppy snapshot data"));
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
                        && "retry unavailable".equals(batch.getErrorMessage())
        ));
    }

    @Test
    void rejectsSmoothTrendWhenTrendIsVolatile() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        googSnapshot.setTrend("volatile downtrend");
        googSnapshot.setPriceConsistency("uneven downward movement");
        String invalidJson = googSuggestionJson(
                "Smooth trend learning",
                "GOOG gives smooth trend practice with moderate risk and complete data.",
                "GOOG shows a smooth trend with moderate risk, moderate volatility, stable volume, price consistency, and complete data for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                OpenAiSuggestionResult.failure("retry unavailable")
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        assertTrue(promptCaptor.getAllValues().get(1).contains("AI wording contradicts volatile or choppy snapshot data"));
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
                        && "retry unavailable".equals(batch.getErrorMessage())
        ));
    }

    @Test
    void rejectsClearTrendWhenSnapshotIsVolatileAndChoppy() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        googSnapshot.setTrend("volatile downtrend");
        googSnapshot.setPriceConsistency("choppy downward movement");
        String invalidJson = googSuggestionJson(
                "Clear trend practice",
                "GOOG gives clear trend practice with moderate risk and complete data.",
                "GOOG has a clear trend with moderate risk, moderate volatility, stable volume, price consistency, and complete data for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                OpenAiSuggestionResult.failure("retry unavailable")
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        assertTrue(promptCaptor.getAllValues().get(1).contains("AI wording says clear trend without supporting snapshot data"));
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
                        && "retry unavailable".equals(batch.getErrorMessage())
        ));
    }

    @Test
    void rejectsStableMovementButAllowsStableVolumeWhenSnapshotIsVolatileAndChoppy() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        googSnapshot.setTrend("volatile downtrend");
        googSnapshot.setPriceConsistency("choppy downward movement");
        String invalidJson = googSuggestionJson(
                "Stable movement learning",
                "GOOG gives stable movement practice with moderate risk and complete data.",
                "GOOG has stable movement with moderate risk, moderate volatility, stable volume, price consistency, and complete data for educational paper-trading practice."
        );
        String validRetryJson = googSuggestionJson(
                "Choppy movement learning",
                "GOOG gives choppy paper-trading practice with moderate risk and complete data.",
                "GOOG is shown as an educational paper-trading example because the snapshot has a volatile downtrend, moderate volatility, stable volume, choppy price consistency, moderate risk, and complete data."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                new OpenAiSuggestionResult(true, validRetryJson, 100, 80, 180, "stop", null)
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        assertTrue(promptCaptor.getAllValues().get(1).contains("AI stable wording contradicts volatile or choppy snapshot data"));
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS
                        && batch.getErrorMessage() == null
        ));
    }

    @Test
    void bannedAdviceWordingInSuggestionLabelIsRejected() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        String invalidJson = googSuggestionJson(
                "Must buy practice",
                "GOOG gives paper-trading practice with moderate risk and complete data.",
                "GOOG has a strong uptrend, moderate volatility, stable volume, smooth price consistency, moderate risk, and complete data for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                OpenAiSuggestionResult.failure("retry unavailable")
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        assertTrue(promptCaptor.getAllValues().get(1).contains("AI returned advice, prediction, or unsupported external reason"));
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
                        && "retry unavailable".equals(batch.getErrorMessage())
        ));
    }

    @Test
    void acceptsHonestChoppyMovementWording() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        googSnapshot.setTrend("volatile downtrend");
        googSnapshot.setPriceConsistency("choppy downward movement");
        String honestJson = googSuggestionJson(
                "Choppy movement learning",
                "GOOG gives choppy paper-trading practice with moderate risk and complete data.",
                "GOOG is shown as an educational paper-trading example because the snapshot has a volatile downtrend, moderate volatility, stable volume, choppy price consistency, moderate risk, and complete data."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, honestJson, 100, 80, 180, "stop", null)
        );

        verify(openAiClient).generateSuggestion(anyString(), anyString());
        assertEquals(1, promptCaptor.getAllValues().size());
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS
                        && batch.getErrorMessage() == null
        ));
    }

    @Test
    void highBehaviorSummaryCanRelaxAggressiveGuardrailWhenConflictIsExplained() {
        UserInvestmentProfile profile = profile();
        profile.setRiskTolerance(RiskTolerance.CONSERVATIVE);
        profile.setBehaviorConfidence(null);
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 9, 0),
                30L,
                BehaviorConfidence.HIGH,
                UserBehaviorStyle.AGGRESSIVE,
                88,
                "aggressive",
                "AMD,NVDA"
        ));
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(new OpenAiSuggestionResult(
                true,
                aggressiveSuggestionWithConflictJson(),
                100,
                80,
                180,
                "stop",
                null
        ));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(12L);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems);

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals("SUCCESS", response.batchStatus());
        assertEquals("AMD", savedItems.get(0).getSymbol());
        assertTrue(savedItems.get(0).getDetailReason().contains("onboarding profile was conservative"));
        assertTrue(savedItems.get(0).getDetailReason().contains("paper-trading behavior"));
    }

    @Test
    void existingFallbackCachedSameInputHashCreatesNewSuccessBatchAndPreservesFallbackHistory() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch fallbackCached = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        fallbackCached.setSuggestionBatchId(90L);
        fallbackCached.setPromptVersion("stock-suggestion-v2");
        fallbackCached.setTriggerReason(StockAiSuggestionTriggerReason.MANUAL_REFRESH);
        fallbackCached.setCreatedAt(LocalDateTime.now().minusHours(2));
        fallbackCached.setUpdatedAt(LocalDateTime.now().minusHours(2));
        fallbackCached.setExpiresAt(LocalDateTime.now().plusHours(20));
        StockAiSuggestionItem fallbackCachedMsft = suggestionItem(fallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        fallbackCachedMsft.setSuggestionItemId(91L);
        fallbackCachedMsft.setAnalysisSnapshot(snapshot("MSFT"));
        StockAiSuggestionItem fallbackCachedAapl = suggestionItem(fallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        fallbackCachedAapl.setSuggestionItemId(92L);
        fallbackCachedAapl.setSymbol("AAPL");
        fallbackCachedAapl.setRankNo(2);
        fallbackCachedAapl.setAnalysisSnapshot(snapshot("AAPL"));
        List<StockAiSuggestionItem> batchItems = new ArrayList<>(List.of(fallbackCachedMsft, fallbackCachedAapl));
        AtomicReference<StockAiSuggestionBatch> savedSuccessBatch = new AtomicReference<>();
        List<StockAiSuggestionItem> savedSuccessItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.of(fallbackCached));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.of(fallbackCached));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(new OpenAiSuggestionResult(
                true,
                validSuggestionJson(),
                100,
                80,
                180,
                "stop",
                null
        ));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE))
                .thenReturn(List.of(fallbackCachedMsft, fallbackCachedAapl));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(91L);
            savedSuccessBatch.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() == savedSuccessBatch.get()) {
                item.setSuggestionItemId((long) savedSuccessItems.size() + 100L);
                savedSuccessItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    if (batch == savedSuccessBatch.get()) {
                        return savedSuccessItems.stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                    }
                    return batchItems.stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                });

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(91L, response.batchId());
        assertEquals("SUCCESS", response.batchStatus());
        assertFalse(response.fallbackUsed());
        assertFalse(response.refreshAllowed());
        assertNotNull(response.nextRefreshAllowedAt());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_CACHED, fallbackCached.getStatus());
        assertNull(fallbackCached.getErrorMessage());
        assertNull(fallbackCached.getTotalTokens());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, savedSuccessBatch.get().getStatus());
        assertEquals(Integer.valueOf(180), savedSuccessBatch.get().getTotalTokens());
        assertEquals("Microsoft is a profile-aligned paper-trading learning suggestion based on structured data.", savedSuccessBatch.get().getBatchSummary());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, fallbackCachedMsft.getStatus());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, fallbackCachedAapl.getStatus());
        assertEquals(1, savedSuccessItems.size());
        StockAiSuggestionItem savedMsft = savedSuccessItems.get(0);
        assertEquals(savedSuccessBatch.get(), savedMsft.getSuggestionBatch());
        assertEquals("MSFT", savedMsft.getSymbol());
        assertEquals(1, savedMsft.getRankNo());
        assertEquals(76, savedMsft.getMatchScore());
        assertEquals("moderate", savedMsft.getRiskLevel());
        assertEquals("Balanced trend learning", savedMsft.getSuggestionLabel());
        assertEquals("Microsoft matches your moderate learning profile.", savedMsft.getShortReason());
        assertEquals("Microsoft has a strong uptrend, moderate volatility, and moderate risk with complete data for beginner-friendly paper-trading practice.", savedMsft.getDetailReason());
        assertEquals(snapshot("MSFT").getAnalysisSnapshotId(), savedMsft.getAnalysisSnapshot().getAnalysisSnapshotId());
        verify(batchRepository, times(1)).save(same(savedSuccessBatch.get()));
        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
    }

    @Test
    void existingFallbackCachedSameInputHashCreatesNewRuleBasedBatchAndPreservesFallbackHistory() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch fallbackCached = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        fallbackCached.setSuggestionBatchId(95L);
        fallbackCached.setPromptVersion("stock-suggestion-v2");
        fallbackCached.setCreatedAt(LocalDateTime.now().minusHours(2));
        fallbackCached.setExpiresAt(LocalDateTime.now().plusHours(20));
        StockAiSuggestionItem fallbackCachedMsft = suggestionItem(fallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        fallbackCachedMsft.setSuggestionItemId(96L);
        fallbackCachedMsft.setAnalysisSnapshot(snapshot("MSFT"));
        List<StockAiSuggestionItem> batchItems = new ArrayList<>(List.of(fallbackCachedMsft));
        AtomicReference<StockAiSuggestionBatch> savedFallbackBatch = new AtomicReference<>();
        List<StockAiSuggestionItem> savedFallbackItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.of(fallbackCached));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.of(fallbackCached));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE))
                .thenReturn(List.of(fallbackCachedMsft));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(97L);
            savedFallbackBatch.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() == savedFallbackBatch.get()) {
                item.setSuggestionItemId((long) savedFallbackItems.size() + 100L);
                savedFallbackItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    if (batch == savedFallbackBatch.get()) {
                        return savedFallbackItems.stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                    }
                    return batchItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(97L, response.batchId());
        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        assertFalse(response.refreshAllowed());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_CACHED, fallbackCached.getStatus());
        assertNull(fallbackCached.getErrorMessage());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, savedFallbackBatch.get().getStatus());
        assertEquals("OpenAI unavailable", savedFallbackBatch.get().getErrorMessage());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, fallbackCachedMsft.getStatus());
        assertFalse(savedFallbackItems.isEmpty());
        verify(batchRepository, times(1)).save(same(savedFallbackBatch.get()));
        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
    }

    @Test
    void secondManualRefreshWithinCooldownReturnsExistingBatchWithoutCreatingAnotherBatch() {
        UserInvestmentProfile profile = profile();
        AtomicReference<StockAiSuggestionBatch> savedBatch = new AtomicReference<>();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(savedBatch.get()));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenAnswer(invocation -> Optional.ofNullable(savedBatch.get()));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(new OpenAiSuggestionResult(
                true,
                validSuggestionJson(),
                100,
                80,
                180,
                "stop",
                null
        ));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(12L);
            savedBatch.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            item.setSuggestionItemId((long) savedItems.size() + 40L);
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems);

        StockAiSuggestionResponse first = service.refreshSuggestionsForCurrentUser();
        StockAiSuggestionResponse second = service.refreshSuggestionsForCurrentUser();

        assertEquals("SUCCESS", first.batchStatus());
        assertEquals(first.batchId(), second.batchId());
        assertEquals("SUCCESS", second.batchStatus());
        assertFalse(second.fallbackUsed());
        assertFalse(second.refreshAllowed());
        assertNotNull(second.nextRefreshAllowedAt());
        assertEquals(savedBatch.get().getCreatedAt().plusHours(1), second.nextRefreshAllowedAt());
        verify(batchRepository, times(1)).save(any(StockAiSuggestionBatch.class));
        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
    }

    @Test
    void scheduledRefreshBypassesManualCooldownButReusesSameInputHash() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingBatch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        StockAiSuggestionBatch recentManualBatch = batch(profile, StockAiSuggestionBatchStatus.SUCCESS);
        recentManualBatch.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.of(recentManualBatch));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.of(existingBatch));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingBatch), anyCollection()))
                .thenReturn(List.of());

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals(existingBatch.getSuggestionBatchId(), response.batchId());
        assertTrue(response.message().contains("unchanged"));
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
    }

    @Test
    void lowBehaviorWeightAndPromptSectionsAreIncluded() {
        UserInvestmentProfile profile = profile();
        LocalDateTime firstBehaviorUpdate = LocalDateTime.now().minusHours(2);
        LocalDateTime secondBehaviorUpdate = LocalDateTime.now().minusHours(1);

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(batch.getSuggestionBatchId() == null ? 20L : batch.getSuggestionBatchId());
            return batch;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection())).thenReturn(List.of());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L))
                .thenReturn(behaviorSummary(firstBehaviorUpdate))
                .thenReturn(behaviorSummary(secondBehaviorUpdate));

        service.refreshSuggestionsForCurrentUser();
        service.refreshSuggestionsForCurrentUser();

        verify(batchRepository, times(2)).save(argThat(batch -> batch.getInputHash() != null));
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), contains("\"declaredOnboardingProfile\""));
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), contains("\"observedPaperTradingBehavior\""));
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), contains("\"personalizationWeight\":{\"onboardingWeight\":80,\"behaviorWeight\":20"));
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), contains("\"candidateFitSignals\""));
    }

    @Test
    void profileFieldsAreIncludedInInputHash() {
        LocalDateTime behaviorUpdatedAt = LocalDateTime.of(2026, 6, 8, 9, 0);
        BehaviorSummaryForSuggestion summary = behaviorSummary(behaviorUpdatedAt);
        String baseline = inputHashForGeneratedFallback(profile(), summary);

        UserInvestmentProfile riskChanged = profile();
        riskChanged.setRiskTolerance(RiskTolerance.CONSERVATIVE);
        assertNotEquals(baseline, inputHashForGeneratedFallback(riskChanged, summary));

        UserInvestmentProfile goalChanged = profile();
        goalChanged.setInvestmentGoal(InvestmentGoal.STABLE);
        assertNotEquals(baseline, inputHashForGeneratedFallback(goalChanged, summary));

        UserInvestmentProfile experienceChanged = profile();
        experienceChanged.setExperienceLevel(ExperienceLevel.INTERMEDIATE);
        assertNotEquals(baseline, inputHashForGeneratedFallback(experienceChanged, summary));

        UserInvestmentProfile volatilityChanged = profile();
        volatilityChanged.setPreferredVolatility(PreferredVolatility.LOW);
        assertNotEquals(baseline, inputHashForGeneratedFallback(volatilityChanged, summary));

        UserInvestmentProfile horizonChanged = profile();
        horizonChanged.setPreferredHorizon(PreferredHorizon.LONG_TERM);
        assertNotEquals(baseline, inputHashForGeneratedFallback(horizonChanged, summary));

        UserInvestmentProfile scoresChanged = profile();
        scoresChanged.setRiskScore(80);
        scoresChanged.setGoalScore(30);
        scoresChanged.setExperienceScore(60);
        assertNotEquals(baseline, inputHashForGeneratedFallback(scoresChanged, summary));

        UserInvestmentProfile sourceChanged = profile();
        sourceChanged.setProfileSource(ProfileSource.RETAKE_QUIZ);
        assertNotEquals(baseline, inputHashForGeneratedFallback(sourceChanged, summary));

        UserInvestmentProfile versionChanged = profile();
        versionChanged.setProfileVersion(2);
        assertNotEquals(baseline, inputHashForGeneratedFallback(versionChanged, summary));
    }

    @Test
    void behaviorSummaryUpdatedAtDoesNotChangeInputHash() {
        UserInvestmentProfile profile = profile();
        String firstHash = inputHashForGeneratedFallback(profile, behaviorSummary(LocalDateTime.of(2026, 6, 8, 9, 0)));
        String secondHash = inputHashForGeneratedFallback(profile, behaviorSummary(LocalDateTime.of(2026, 6, 8, 10, 0)));

        assertEquals(firstHash, secondHash);
    }

    @Test
    void meaningfulBehaviorFieldsChangeInputHash() {
        UserInvestmentProfile profile = profile();
        String baseline = inputHashForGeneratedFallback(profile, behaviorSummary(LocalDateTime.of(2026, 6, 8, 9, 0)));
        String changed = inputHashForGeneratedFallback(profile, behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 9, 0),
                30L,
                BehaviorConfidence.HIGH,
                UserBehaviorStyle.AGGRESSIVE,
                82,
                "aggressive",
                "AMD,NVDA"
        ));

        assertNotEquals(baseline, changed);
    }

    @Test
    void investmentProfileBehaviorColumnsDoNotChangeInputHash() {
        BehaviorSummaryForSuggestion summary = behaviorSummary(LocalDateTime.of(2026, 6, 8, 9, 0));
        UserInvestmentProfile baselineProfile = profile();
        String baseline = inputHashForGeneratedFallback(baselineProfile, summary);

        UserInvestmentProfile oldBehaviorColumnsChanged = profile();
        oldBehaviorColumnsChanged.setBehaviorConfidence(BehaviorConfidence.HIGH);
        oldBehaviorColumnsChanged.setBehaviorRiskScore(95);
        oldBehaviorColumnsChanged.setBehaviorStyle(BehaviorStyle.SPECULATIVE);

        assertEquals(baseline, inputHashForGeneratedFallback(oldBehaviorColumnsChanged, summary));
    }

    @Test
    void promptIncludesSortedCandidateFitSignalsWithConservativeGuardrail() throws Exception {
        UserInvestmentProfile profile = profile();
        profile.setRiskTolerance(RiskTolerance.CONSERVATIVE);
        profile.setPreferredVolatility(PreferredVolatility.LOW);
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(21L);
            return batch;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection())).thenReturn(List.of());

        service.refreshSuggestionsForCurrentUser();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), promptCaptor.capture());

        String userContent = promptCaptor.getAllValues().get(0);
        JsonNode root = new ObjectMapper().readTree(userContent);
        JsonNode candidateSignals = root.path("candidateFitSignals");

        assertTrue(root.has("declaredOnboardingProfile"));
        assertTrue(root.has("observedPaperTradingBehavior"));
        assertEquals(80, root.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(20, root.path("personalizationWeight").path("behaviorWeight").asInt());
        assertTrue(root.has("supportedStockUniverse"));
        assertTrue(root.has("beginnerSafetyRules"));
        assertEquals(8, candidateSignals.size());
        assertEquals("AAPL", candidateSignals.get(0).path("symbol").asText());
        assertEquals("AMD", candidateSignals.get(1).path("symbol").asText());
        assertEquals("MISMATCH", candidateSignals.get(1).path("riskCompatibility").asText());
        assertEquals("INSUFFICIENT_DATA", candidateSignals.get(1).path("behaviorCompatibility").asText());

        assertTrue(userContent.contains("candidateFitSignals"));
        assertFalse(userContent.contains("backendFitHint"));
        assertFalse(userContent.contains("backendFitScore"));
        assertFalse(userContent.contains("watchlist"));

        for (JsonNode signal : candidateSignals) {
            assertTrue(signal.has("symbol"));
            assertTrue(signal.has("riskCategory"));
            assertTrue(signal.has("riskCompatibility"));
            assertTrue(signal.has("behaviorCompatibility"));
            assertTrue(signal.has("dataQualityLabel"));
            assertTrue(signal.has("onboardingFitScore"));
            assertTrue(signal.has("behaviorFitScore"));
            assertTrue(signal.has("combinedFitScore"));
            assertTrue(signal.has("dataQualityScore"));
            assertTrue(signal.has("dataQualityPenalty"));
            assertTrue(signal.has("warningSignals"));
            assertTrue(signal.has("snapshotHash"));
            assertTrue(signal.has("fitNotes"));

            assertFalse(signal.has("backendFitHint"));
            assertFalse(signal.has("backendFitScore"));
        }
    }

    @Test
    void noBehaviorProfileUsesLowNoDataSummaryAndEightyTwentyWeight() throws Exception {
        String userContent = promptForBehaviorSummary(behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 9, 0),
                null,
                BehaviorConfidence.LOW,
                UserBehaviorStyle.INSUFFICIENT_DATA,
                null,
                null,
                null
        ));

        JsonNode root = new ObjectMapper().readTree(userContent);

        assertFalse(root.path("observedPaperTradingBehavior").path("hasBehaviorProfile").asBoolean());
        assertEquals("LOW", root.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals(80, root.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(20, root.path("personalizationWeight").path("behaviorWeight").asInt());
        assertTrue(userContent.contains("declared onboarding preferences remain the primary signal"));
    }

    @Test
    void nullBehaviorSummaryUsesDefensiveLowNoDataFallback() throws Exception {
        String userContent = promptForBehaviorSummary(null);

        JsonNode root = new ObjectMapper().readTree(userContent);

        assertFalse(root.path("observedPaperTradingBehavior").path("hasBehaviorProfile").asBoolean());
        assertEquals("LOW", root.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals(80, root.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(20, root.path("personalizationWeight").path("behaviorWeight").asInt());
    }

    @Test
    void mediumBehaviorUsesFortySixtyWeight() throws Exception {
        String userContent = promptForBehaviorSummary(behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 9, 0),
                30L,
                BehaviorConfidence.MEDIUM,
                UserBehaviorStyle.BALANCED,
                58,
                "moderate",
                "MSFT,AAPL"
        ));

        JsonNode root = new ObjectMapper().readTree(userContent);

        assertEquals("MEDIUM", root.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals(40, root.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(60, root.path("personalizationWeight").path("behaviorWeight").asInt());
        assertTrue(root.path("candidateFitSignals").get(0).has("behaviorFitScore"));
    }

    @Test
    void highBehaviorUsesTenNinetyWeight() throws Exception {
        String userContent = promptForBehaviorSummary(behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 9, 0),
                30L,
                BehaviorConfidence.HIGH,
                UserBehaviorStyle.AGGRESSIVE,
                82,
                "aggressive",
                "AMD,NVDA"
        ));

        JsonNode root = new ObjectMapper().readTree(userContent);

        assertEquals("HIGH", root.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals(10, root.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(90, root.path("personalizationWeight").path("behaviorWeight").asInt());
    }

    @Test
    void candidateFitChangeUpdatesInputHashEvenWhenSnapshotHashIsUnchanged() {
        UserInvestmentProfile profile = profile();
        AtomicBoolean weakMicrosoftData = new AtomicBoolean(false);
        List<String> savedHashes = new ArrayList<>();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> {
            StockAnalysisSnapshot snapshot = snapshot(invocation.getArgument(0));
            if (weakMicrosoftData.get() && "MSFT".equals(snapshot.getSymbol())) {
                snapshot.setIsFallback(true);
                snapshot.setMissingDataCount(4);
            }
            return snapshot;
        });
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId((long) savedHashes.size() + 31L);
            savedHashes.add(batch.getInputHash());
            return batch;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection())).thenReturn(List.of());

        service.refreshSuggestionsForCurrentUser();
        weakMicrosoftData.set(true);
        service.refreshSuggestionsForCurrentUser();

        assertEquals(2, savedHashes.size());
        assertNotEquals(savedHashes.get(0), savedHashes.get(1));
    }

    @Test
    void openAiFailureWithCachedSuccessCreatesCooldownBatchAndSecondRefreshDoesNotCallOpenAiAgain() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch cachedSuccess = batch(profile, StockAiSuggestionBatchStatus.SUCCESS);
        cachedSuccess.setSuggestionBatchId(70L);
        cachedSuccess.setCreatedAt(LocalDateTime.now().minusDays(1));
        cachedSuccess.setUpdatedAt(LocalDateTime.now().minusDays(1));
        cachedSuccess.setExpiresAt(LocalDateTime.now().plusHours(2));
        StockAiSuggestionItem cachedItem = suggestionItem(cachedSuccess, StockAiSuggestionItemStatus.ACTIVE);
        cachedItem.setAnalysisSnapshot(snapshot("MSFT"));
        StockAiSuggestionItem watchlistedCachedItem = suggestionItem(cachedSuccess, StockAiSuggestionItemStatus.WATCHLISTED);
        watchlistedCachedItem.setSuggestionItemId(21L);
        watchlistedCachedItem.setSymbol("AAPL");
        watchlistedCachedItem.setRankNo(2);
        watchlistedCachedItem.setMatchScore(72);
        watchlistedCachedItem.setRiskLevel("moderate");
        watchlistedCachedItem.setSuggestionLabel("Watchlisted learning pick");
        watchlistedCachedItem.setShortReason("Apple remains available from the cached suggestion.");
        watchlistedCachedItem.setDetailReason("Apple has moderate risk and complete data in the cached suggestion.");
        watchlistedCachedItem.setAnalysisSnapshot(snapshot("AAPL"));
        AtomicReference<StockAiSuggestionBatch> savedFallbackCached = new AtomicReference<>();
        List<StockAiSuggestionItem> copiedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenAnswer(invocation -> savedFallbackCached.get() == null
                        ? Optional.of(cachedSuccess)
                        : Optional.of(savedFallbackCached.get()));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenAnswer(invocation -> Optional.ofNullable(savedFallbackCached.get()));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(batchRepository.findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.of(cachedSuccess));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of(cachedItem));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(71L);
            savedFallbackCached.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() != cachedSuccess) {
                item.setSuggestionItemId((long) copiedItems.size() + 80L);
                copiedItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    if (batch == cachedSuccess) {
                        return List.of(cachedItem, watchlistedCachedItem).stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                    }
                    return copiedItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse first = service.refreshSuggestionsForCurrentUser();
        StockAiSuggestionResponse second = service.refreshSuggestionsForCurrentUser();

        assertEquals("FALLBACK_CACHED", first.batchStatus());
        assertTrue(first.fallbackUsed());
        assertFalse(first.refreshAllowed());
        assertNotNull(first.nextRefreshAllowedAt());
        assertEquals(71L, first.batchId());
        assertEquals(first.batchId(), second.batchId());
        assertEquals("FALLBACK_CACHED", second.batchStatus());
        assertTrue(second.fallbackUsed());
        assertFalse(second.refreshAllowed());
        assertEquals(first.nextRefreshAllowedAt(), second.nextRefreshAllowedAt());
        assertEquals(2, copiedItems.size());
        StockAiSuggestionItem copiedActiveItem = copiedItems.get(0);
        assertEquals(savedFallbackCached.get(), copiedActiveItem.getSuggestionBatch());
        assertEquals("MSFT", copiedActiveItem.getSymbol());
        assertEquals(1, copiedActiveItem.getRankNo());
        assertEquals(80, copiedActiveItem.getMatchScore());
        assertEquals("moderate", copiedActiveItem.getRiskLevel());
        assertEquals("Profile-aligned learning pick", copiedActiveItem.getSuggestionLabel());
        assertEquals("Microsoft matches your profile using risk and data completeness.", copiedActiveItem.getShortReason());
        assertEquals("Microsoft has moderate risk with stable volatility and complete data.", copiedActiveItem.getDetailReason());
        assertEquals(cachedItem.getAnalysisSnapshot(), copiedActiveItem.getAnalysisSnapshot());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, copiedActiveItem.getStatus());
        StockAiSuggestionItem copiedWatchlistedItem = copiedItems.get(1);
        assertEquals(savedFallbackCached.get(), copiedWatchlistedItem.getSuggestionBatch());
        assertEquals("AAPL", copiedWatchlistedItem.getSymbol());
        assertEquals(StockAiSuggestionItemStatus.WATCHLISTED, copiedWatchlistedItem.getStatus());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, cachedItem.getStatus());
        assertEquals(StockAiSuggestionItemStatus.WATCHLISTED, watchlistedCachedItem.getStatus());
        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        verify(batchRepository, times(1)).save(any(StockAiSuggestionBatch.class));
        verify(itemRepository).save(cachedItem);
    }

    @Test
    void openAiFailureReusesHistoricalSuccessWhenNoNonExpiredSuccessExists() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch historicalSuccess = batch(profile, StockAiSuggestionBatchStatus.SUCCESS);
        historicalSuccess.setSuggestionBatchId(75L);
        historicalSuccess.setCreatedAt(LocalDateTime.now().minusDays(8));
        historicalSuccess.setExpiresAt(LocalDateTime.now().minusDays(7));
        StockAiSuggestionItem cachedItem = suggestionItem(historicalSuccess, StockAiSuggestionItemStatus.ACTIVE);
        cachedItem.setAnalysisSnapshot(snapshot("MSFT"));
        AtomicReference<StockAiSuggestionBatch> savedFallbackCached = new AtomicReference<>();
        List<StockAiSuggestionItem> copiedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(batchRepository.findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(1L, StockAiSuggestionBatchStatus.SUCCESS))
                .thenReturn(Optional.of(historicalSuccess));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(76L);
            savedFallbackCached.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            copiedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> invocation.getArgument(0) == historicalSuccess
                        ? List.of(cachedItem)
                        : copiedItems);

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals("FALLBACK_CACHED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        assertEquals(76L, response.batchId());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_CACHED, savedFallbackCached.get().getStatus());
        assertEquals("OpenAI unavailable", savedFallbackCached.get().getErrorMessage());
        assertEquals(1, copiedItems.size());
    }

    @Test
    void watchlistActiveSuggestionItemSucceeds() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch batch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        StockAiSuggestionItem item = suggestionItem(batch, StockAiSuggestionItemStatus.ACTIVE);

        when(itemRepository.findBySuggestionItemIdAndUserUserId(20L, 1L)).thenReturn(Optional.of(item));
        when(watchlistRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(batch), anyCollection()))
                .thenReturn(List.of(item));

        StockAiSuggestionResponse response = service.watchlistSuggestionForCurrentUser(20L);

        assertEquals("Suggestion added to watchlist", response.message());
        assertEquals(StockAiSuggestionItemStatus.WATCHLISTED, item.getStatus());
        verify(watchlistRepository).save(any(UserWatchlist.class));
        verify(itemRepository).save(item);
    }

    @Test
    void watchlistAlreadyWatchlistedSuggestionItemIsIdempotent() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch batch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        StockAiSuggestionItem item = suggestionItem(batch, StockAiSuggestionItemStatus.WATCHLISTED);
        UserWatchlist existingWatchlist = new UserWatchlist();
        existingWatchlist.setUser(user);
        existingWatchlist.setSymbol("MSFT");

        when(itemRepository.findBySuggestionItemIdAndUserUserId(20L, 1L)).thenReturn(Optional.of(item));
        when(watchlistRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(existingWatchlist));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(batch), anyCollection()))
                .thenReturn(List.of(item));

        StockAiSuggestionResponse response = service.watchlistSuggestionForCurrentUser(20L);

        assertEquals("Suggestion added to watchlist", response.message());
        assertEquals(StockAiSuggestionItemStatus.WATCHLISTED, item.getStatus());
        verify(watchlistRepository).save(existingWatchlist);
        verify(itemRepository).save(item);
    }

    @Test
    void watchlistDismissedSuggestionItemIsRejected() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch batch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        StockAiSuggestionItem item = suggestionItem(batch, StockAiSuggestionItemStatus.DISMISSED);

        when(itemRepository.findBySuggestionItemIdAndUserUserId(20L, 1L)).thenReturn(Optional.of(item));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.watchlistSuggestionForCurrentUser(20L)
        );

        assertTrue(exception.getMessage().contains("Only active or already watchlisted suggestion items"));
        assertEquals(StockAiSuggestionItemStatus.DISMISSED, item.getStatus());
        verify(watchlistRepository, never()).save(any(UserWatchlist.class));
        verify(itemRepository, never()).save(any(StockAiSuggestionItem.class));
    }

    @Test
    void watchlistExpiredSuggestionItemIsRejected() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch batch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        StockAiSuggestionItem item = suggestionItem(batch, StockAiSuggestionItemStatus.EXPIRED);

        when(itemRepository.findBySuggestionItemIdAndUserUserId(20L, 1L)).thenReturn(Optional.of(item));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.watchlistSuggestionForCurrentUser(20L)
        );

        assertTrue(exception.getMessage().contains("Only active or already watchlisted suggestion items"));
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, item.getStatus());
        verify(watchlistRepository, never()).save(any(UserWatchlist.class));
        verify(itemRepository, never()).save(any(StockAiSuggestionItem.class));
    }

    private UserInvestmentProfile profile() {
        UserInvestmentProfile profile = new UserInvestmentProfile();
        profile.setProfileId(2L);
        profile.setUser(user);
        profile.setRiskTolerance(RiskTolerance.MODERATE);
        profile.setInvestmentGoal(InvestmentGoal.LEARNING);
        profile.setExperienceLevel(ExperienceLevel.BEGINNER);
        profile.setPreferredVolatility(PreferredVolatility.MEDIUM);
        profile.setPreferredHorizon(PreferredHorizon.MEDIUM_TERM);
        profile.setRiskScore(50);
        profile.setGoalScore(50);
        profile.setExperienceScore(20);
        profile.setBehaviorConfidence(BehaviorConfidence.LOW);
        profile.setProfileSource(ProfileSource.ONBOARDING);
        profile.setProfileVersion(1);
        profile.setCreatedAt(LocalDateTime.now().minusDays(1));
        profile.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return profile;
    }

    private String inputHashForGeneratedFallback(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary
    ) {
        reset(
                profileRepository,
                batchRepository,
                itemRepository,
                stockAnalysisService,
                openAiClient,
                behaviorProfileService
        );

        AtomicReference<String> inputHash = new AtomicReference<>();
        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v2"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        lenient().when(batchRepository.findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS),
                any()
        )).thenReturn(Optional.empty());
        lenient().when(batchRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS)
        )).thenReturn(Optional.empty());
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            inputHash.set(batch.getInputHash());
            batch.setSuggestionBatchId(90L);
            return batch;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection())).thenReturn(List.of());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(behaviorSummary);

        service.generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false);

        assertNotNull(inputHash.get());
        return inputHash.get();
    }

    private String promptForBehaviorSummary(BehaviorSummaryForSuggestion behaviorSummary) {
        reset(
                profileRepository,
                batchRepository,
                itemRepository,
                stockAnalysisService,
                openAiClient,
                behaviorProfileService
        );

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile()));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v2"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        lenient().when(batchRepository.findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS),
                any()
        )).thenReturn(Optional.empty());
        lenient().when(batchRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS)
        )).thenReturn(Optional.empty());
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(91L);
            return batch;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection())).thenReturn(List.of());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(behaviorSummary);

        service.generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), promptCaptor.capture());
        return promptCaptor.getAllValues().get(0);
    }

    private StockAiSuggestionBatch batch(UserInvestmentProfile profile, StockAiSuggestionBatchStatus status) {
        StockAiSuggestionBatch batch = new StockAiSuggestionBatch();
        batch.setSuggestionBatchId(5L);
        batch.setUser(user);
        batch.setProfile(profile);
        batch.setProfileVersion(profile.getProfileVersion());
        batch.setModel("gpt-4o-mini");
        batch.setPromptVersion("stock-suggestion-v2");
        batch.setStatus(status);
        batch.setTriggerReason(StockAiSuggestionTriggerReason.MANUAL_REFRESH);
        batch.setInputHash("hash");
        batch.setBatchSummary("summary");
        batch.setAnalysisTimeframe("7D");
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batch.setExpiresAt(LocalDateTime.now().plusHours(24));
        return batch;
    }

    private BehaviorSummaryForSuggestion behaviorSummary(LocalDateTime updatedAt) {
        return behaviorSummary(
                updatedAt,
                30L,
                BehaviorConfidence.LOW,
                UserBehaviorStyle.INSUFFICIENT_DATA,
                null,
                null,
                null
        );
    }

    private BehaviorSummaryForSuggestion behaviorSummary(
            LocalDateTime updatedAt,
            Long behaviorProfileId,
            BehaviorConfidence confidence,
            UserBehaviorStyle style,
            Integer behaviorRiskScore,
            String favoriteRiskCategory,
            String mostTradedSymbols
    ) {
        return new BehaviorSummaryForSuggestion(
                behaviorProfileId,
                null,
                null,
                behaviorRiskScore,
                style,
                confidence,
                null,
                null,
                null,
                null,
                behaviorRiskScore,
                null,
                null,
                null,
                behaviorRiskScore,
                favoriteRiskCategory,
                mostTradedSymbols,
                confidence == BehaviorConfidence.LOW
                        ? "Recent paper-trading activity is still limited."
                        : "Recent paper-trading behavior has meaningful confidence.",
                updatedAt,
                "No paper-trading transaction source exists yet"
        );
    }

    private StockAiSuggestionItem suggestionItem(StockAiSuggestionBatch batch, StockAiSuggestionItemStatus status) {
        StockAiSuggestionItem item = new StockAiSuggestionItem();
        item.setSuggestionItemId(20L);
        item.setSuggestionBatch(batch);
        item.setUser(user);
        item.setSymbol("MSFT");
        item.setRankNo(1);
        item.setMatchScore(80);
        item.setRiskLevel("moderate");
        item.setSuggestionLabel("Profile-aligned learning pick");
        item.setShortReason("Microsoft matches your profile using risk and data completeness.");
        item.setDetailReason("Microsoft has moderate risk with stable volatility and complete data.");
        item.setStatus(status);
        item.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        item.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        return item;
    }

    private String validSuggestionJson() {
        return """
                {
                  "batchSummary": "Microsoft is a profile-aligned paper-trading learning suggestion based on structured data.",
                  "suggestedStocks": [
                    {
                      "symbol": "MSFT",
                      "rankNo": 1,
                      "matchScore": 76,
                      "riskLevel": "moderate",
                      "suggestionLabel": "Balanced trend learning",
                      "shortReason": "Microsoft matches your moderate learning profile.",
                      "detailReason": "Microsoft has a strong uptrend, moderate volatility, and moderate risk with complete data for beginner-friendly paper-trading practice."
                    }
                  ]
                }
                """;
    }

    private String aggressiveSuggestionWithConflictJson() {
        return """
                {
                  "batchSummary": "AMD is selected for educational paper-trading practice because high-confidence behavior changes the suitability ranking.",
                  "suggestedStocks": [
                    {
                      "symbol": "AMD",
                      "rankNo": 1,
                      "matchScore": 82,
                      "riskLevel": "aggressive",
                      "suggestionLabel": "Higher-volatility behavior practice",
                      "shortReason": "AMD is included as a paper-trading learning example because observed behavior differs from onboarding.",
                      "detailReason": "Although your onboarding profile was conservative, your paper-trading behavior shows higher risk tolerance. AMD has an aggressive risk category, moderate volatility, strong uptrend, and complete data, so it is presented as an educational paper-trading example rather than real-money advice."
                    }
                  ]
                }
                """;
    }

    private String googSuggestionJson(String suggestionLabel, String shortReason, String detailReason) {
        return """
                {
                  "batchSummary": "GOOG is considered for educational paper-trading practice based on stored snapshot data.",
                  "suggestedStocks": [
                    {
                      "symbol": "GOOG",
                      "rankNo": 1,
                      "matchScore": 76,
                      "riskLevel": "moderate",
                      "suggestionLabel": "%s",
                      "shortReason": "%s",
                      "detailReason": "%s"
                    }
                  ]
                }
                """.formatted(suggestionLabel, shortReason, detailReason);
    }

    private ArgumentCaptor<String> refreshWithGoogOpenAiResults(
            StockAnalysisSnapshot googSnapshot,
            OpenAiSuggestionResult firstResult,
            OpenAiSuggestionResult... additionalResults
    ) {
        UserInvestmentProfile profile = profile();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            return "GOOG".equals(symbol) ? googSnapshot : snapshot(symbol);
        });
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v2"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(firstResult, additionalResults);
        lenient().when(batchRepository.findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS),
                any()
        )).thenReturn(Optional.empty());
        lenient().when(batchRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS)
        )).thenReturn(Optional.empty());
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(95L);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            item.setSuggestionItemId((long) savedItems.size() + 100L);
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems);

        service.refreshSuggestionsForCurrentUser();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), promptCaptor.capture());
        return promptCaptor;
    }

    private StockAnalysisSnapshot snapshot(String symbol) {
        StockAnalysisSnapshot snapshot = new StockAnalysisSnapshot();
        snapshot.setAnalysisSnapshotId((long) Math.abs(symbol.hashCode()));
        snapshot.setSymbol(symbol);
        snapshot.setTimeframe("7D");
        snapshot.setCurrentPrice(BigDecimal.valueOf(100));
        snapshot.setPercentChange(BigDecimal.ONE);
        snapshot.setHighPrice(BigDecimal.valueOf(105));
        snapshot.setLowPrice(BigDecimal.valueOf(95));
        snapshot.setTrend("strong uptrend");
        snapshot.setVolatilityLabel("moderate");
        snapshot.setVolumeTrend("stable");
        snapshot.setPriceConsistency("smooth upward movement");
        snapshot.setBaselineRiskCategory(risk(symbol));
        snapshot.setRiskCategory(risk(symbol));
        snapshot.setDataSource("stock_price_daily");
        snapshot.setIsFallback(false);
        snapshot.setMissingDataCount(0);
        snapshot.setSnapshotHash("hash-" + symbol);
        snapshot.setCreatedAt(LocalDateTime.now());
        return snapshot;
    }

    private String risk(String symbol) {
        return switch (symbol) {
            case "NVDA", "TSLA", "AMD" -> "aggressive";
            case "KO", "JNJ" -> "conservative";
            default -> "moderate";
        };
    }
}
