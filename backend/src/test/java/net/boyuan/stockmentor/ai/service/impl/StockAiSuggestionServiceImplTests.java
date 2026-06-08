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
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
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
        lenient().when(behaviorProfileService.createLowConfidenceProfileIfMissing(user)).thenReturn(behaviorProfile());
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
        verify(behaviorProfileService, never()).createLowConfidenceProfileIfMissing(any());
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
        verify(behaviorProfileService, never()).createLowConfidenceProfileIfMissing(any());
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.empty());
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
    void existingFallbackCachedSameInputHashIsUpgradedToSuccessWithoutCreatingDuplicateBatch() {
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

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.of(fallbackCached));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.of(fallbackCached));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString()))
                .thenReturn(Optional.of(fallbackCached));
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
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(fallbackCached), anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return batchItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(90L, response.batchId());
        assertEquals("SUCCESS", response.batchStatus());
        assertFalse(response.fallbackUsed());
        assertFalse(response.refreshAllowed());
        assertNotNull(response.nextRefreshAllowedAt());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, fallbackCached.getStatus());
        assertNull(fallbackCached.getErrorMessage());
        assertEquals(Integer.valueOf(180), fallbackCached.getTotalTokens());
        assertEquals("Microsoft is a profile-aligned paper-trading learning suggestion based on structured data.", fallbackCached.getBatchSummary());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, fallbackCachedMsft.getStatus());
        assertEquals(fallbackCached, fallbackCachedMsft.getSuggestionBatch());
        assertEquals("MSFT", fallbackCachedMsft.getSymbol());
        assertEquals(1, fallbackCachedMsft.getRankNo());
        assertEquals(76, fallbackCachedMsft.getMatchScore());
        assertEquals("moderate", fallbackCachedMsft.getRiskLevel());
        assertEquals("Balanced trend learning", fallbackCachedMsft.getSuggestionLabel());
        assertEquals("Microsoft matches your moderate learning profile.", fallbackCachedMsft.getShortReason());
        assertEquals("Microsoft has a strong uptrend, moderate volatility, and moderate risk with complete data for beginner-friendly paper-trading practice.", fallbackCachedMsft.getDetailReason());
        assertEquals(snapshot("MSFT").getAnalysisSnapshotId(), fallbackCachedMsft.getAnalysisSnapshot().getAnalysisSnapshotId());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, fallbackCachedAapl.getStatus());
        verify(batchRepository, times(1)).save(same(fallbackCached));
        verify(itemRepository, never()).save(argThat(item -> item.getSuggestionItemId() == null));
        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
    }

    @Test
    void existingFallbackCachedSameInputHashCanUpgradeToRuleBasedFallbackWithoutDuplicateBatch() {
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString()))
                .thenReturn(Optional.of(fallbackCached));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE))
                .thenReturn(List.of(fallbackCachedMsft));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionItemId() == null) {
                item.setSuggestionItemId((long) batchItems.size() + 100L);
                batchItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(fallbackCached), anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return batchItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(95L, response.batchId());
        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        assertFalse(response.refreshAllowed());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, fallbackCached.getStatus());
        assertEquals("OpenAI unavailable", fallbackCached.getErrorMessage());
        verify(batchRepository, times(1)).save(same(fallbackCached));
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
    void behaviorSummaryIsIncludedInInputHashAndPrompt() {
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.empty());
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
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), contains("\"behaviorProfile\""));
        verify(openAiClient, atLeastOnce()).generateSuggestion(anyString(), contains("\"behaviorSummaryText\":\"Recent paper-trading activity is still limited.\""));
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
    void behaviorSummaryUpdatedAtRemainsIncludedInInputHash() {
        UserInvestmentProfile profile = profile();
        String firstHash = inputHashForGeneratedFallback(profile, behaviorSummary(LocalDateTime.of(2026, 6, 8, 9, 0)));
        String secondHash = inputHashForGeneratedFallback(profile, behaviorSummary(LocalDateTime.of(2026, 6, 8, 10, 0)));

        assertNotEquals(firstHash, secondHash);
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.empty());
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
            assertTrue(signal.has("dataQuality"));
            assertTrue(signal.has("dataQualityPenalty"));
            assertTrue(signal.has("fitNotes"));

            assertFalse(signal.has("backendFitHint"));
            assertFalse(signal.has("backendFitScore"));
        }
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.empty());
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionBatchStatus.SUCCESS), any()))
                .thenReturn(Optional.of(cachedSuccess));
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v2"), anyString()))
                .thenReturn(Optional.empty());
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
        when(batchRepository.findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS),
                any()
        )).thenReturn(Optional.empty());
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            inputHash.set(batch.getInputHash());
            batch.setSuggestionBatchId(90L);
            return batch;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection())).thenReturn(List.of());
        when(behaviorProfileService.createLowConfidenceProfileIfMissing(user)).thenReturn(behaviorProfile());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(behaviorSummary);

        service.generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.SCHEDULED_REFRESH, false);

        assertNotNull(inputHash.get());
        return inputHash.get();
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

    private UserBehaviorProfile behaviorProfile() {
        UserBehaviorProfile profile = new UserBehaviorProfile();
        profile.setBehaviorProfileId(30L);
        profile.setUser(user);
        profile.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        profile.setBehaviorConfidence(BehaviorConfidence.LOW);
        profile.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        profile.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        return profile;
    }

    private BehaviorSummaryForSuggestion behaviorSummary(LocalDateTime updatedAt) {
        return new BehaviorSummaryForSuggestion(
                30L,
                null,
                null,
                null,
                UserBehaviorStyle.INSUFFICIENT_DATA,
                BehaviorConfidence.LOW,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Recent paper-trading activity is still limited.",
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
