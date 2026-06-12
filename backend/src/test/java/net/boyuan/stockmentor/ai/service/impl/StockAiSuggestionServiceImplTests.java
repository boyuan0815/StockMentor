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
import net.boyuan.stockmentor.userbehavior.model.HighVolatilityExposure;
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
import java.util.Comparator;
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
        lenient().when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
                        eq(1L),
                        anyString(),
                        eq("stock-suggestion-v3"),
                        anyString()
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenReturn(Optional.of(existingBatch));
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingBatch)).thenReturn(List.of());
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingBatch), anyCollection()))
                .thenReturn(List.of());

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(existingBatch.getSuggestionBatchId(), response.batchId());
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
    }

    @Test
    void reusableSameInputBatchRefreshesExpiredItemsInsteadOfReturningEmptyResponse() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingBatch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        LocalDateTime oldExpiresAt = LocalDateTime.now().minusHours(2);
        existingBatch.setExpiresAt(oldExpiresAt);
        existingBatch.setUpdatedAt(LocalDateTime.now().minusHours(3));
        List<StockAiSuggestionItem> expiredItems = new ArrayList<>(List.of(
                suggestionItem(existingBatch, StockAiSuggestionItemStatus.EXPIRED),
                suggestionItem(existingBatch, StockAiSuggestionItemStatus.EXPIRED),
                suggestionItem(existingBatch, StockAiSuggestionItemStatus.EXPIRED)
        ));
        expiredItems.get(0).setSuggestionItemId(21L);
        expiredItems.get(0).setSymbol("KO");
        expiredItems.get(0).setRiskLevel("conservative");
        expiredItems.get(1).setSuggestionItemId(22L);
        expiredItems.get(1).setSymbol("JNJ");
        expiredItems.get(1).setRiskLevel("conservative");
        expiredItems.get(1).setRankNo(2);
        expiredItems.get(2).setSuggestionItemId(23L);
        expiredItems.get(2).setSymbol("MSFT");
        expiredItems.get(2).setRiskLevel("moderate");
        expiredItems.get(2).setRankNo(3);

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v3"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.of(existingBatch));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingBatch), anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return expiredItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingBatch)).thenReturn(expiredItems);
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(existingBatch)).thenReturn(existingBatch);

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals(existingBatch.getSuggestionBatchId(), response.batchId());
        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertEquals(List.of("KO", "JNJ", "MSFT"), response.suggestedStocks().stream().map(stock -> stock.symbol()).toList());
        assertTrue(existingBatch.getExpiresAt().isAfter(oldExpiresAt));
        assertTrue(expiredItems.stream().allMatch(item -> item.getStatus() == StockAiSuggestionItemStatus.ACTIVE));
        verify(batchRepository).save(existingBatch);
        verify(itemRepository, times(3)).save(any(StockAiSuggestionItem.class));
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
    }

    @Test
    void reusableSameInputBatchReactivatesLatestExpiredRowsWhenRanksAreDuplicated() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingBatch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        existingBatch.setExpiresAt(LocalDateTime.now().minusHours(2));
        LocalDateTime oldTime = LocalDateTime.now().minusHours(5);
        LocalDateTime latestTime = LocalDateTime.now().minusMinutes(5);

        StockAiSuggestionItem oldNvda = expiredItem(existingBatch, 31L, "NVDA", 1, oldTime);
        StockAiSuggestionItem oldTsla = expiredItem(existingBatch, 32L, "TSLA", 2, oldTime);
        StockAiSuggestionItem oldAmd = expiredItem(existingBatch, 33L, "AMD", 3, oldTime);
        StockAiSuggestionItem latestKo = expiredItem(existingBatch, 41L, "KO", 1, latestTime);
        latestKo.setRiskLevel("conservative");
        StockAiSuggestionItem latestJnj = expiredItem(existingBatch, 42L, "JNJ", 2, latestTime);
        latestJnj.setRiskLevel("conservative");
        StockAiSuggestionItem latestMsft = expiredItem(existingBatch, 43L, "MSFT", 3, latestTime);
        latestMsft.setRiskLevel("moderate");
        List<StockAiSuggestionItem> batchItems = new ArrayList<>(List.of(
                oldNvda,
                latestKo,
                oldTsla,
                latestJnj,
                oldAmd,
                latestMsft
        ));

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v3"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.of(existingBatch));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingBatch), anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return batchItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .sorted(Comparator.comparing(StockAiSuggestionItem::getRankNo))
                            .toList();
                });
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingBatch)).thenReturn(batchItems);
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(existingBatch)).thenReturn(existingBatch);

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals(List.of("KO", "JNJ", "MSFT"), response.suggestedStocks().stream().map(stock -> stock.symbol()).toList());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, oldNvda.getStatus());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, oldTsla.getStatus());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, oldAmd.getStatus());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, latestKo.getStatus());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, latestJnj.getStatus());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, latestMsft.getStatus());
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
    }

    @Test
    void reusableSameInputBatchExpiresDuplicateVisibleRankAndKeepsNewestActiveRow() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingBatch = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        LocalDateTime oldTime = LocalDateTime.now().minusHours(5);
        LocalDateTime latestTime = LocalDateTime.now().minusMinutes(5);

        StockAiSuggestionItem staleNvda = expiredItem(existingBatch, 51L, "NVDA", 1, oldTime);
        staleNvda.setStatus(StockAiSuggestionItemStatus.ACTIVE);
        staleNvda.setRiskLevel("aggressive");
        StockAiSuggestionItem latestKo = expiredItem(existingBatch, 52L, "KO", 1, latestTime);
        latestKo.setStatus(StockAiSuggestionItemStatus.ACTIVE);
        latestKo.setRiskLevel("conservative");
        StockAiSuggestionItem expiredJnj = expiredItem(existingBatch, 53L, "JNJ", 2, latestTime);
        expiredJnj.setRiskLevel("conservative");
        StockAiSuggestionItem expiredMsft = expiredItem(existingBatch, 54L, "MSFT", 3, latestTime);
        expiredMsft.setRiskLevel("moderate");
        List<StockAiSuggestionItem> batchItems = new ArrayList<>(List.of(staleNvda, latestKo, expiredJnj, expiredMsft));

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v3"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.of(existingBatch));
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingBatch)).thenReturn(batchItems);
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(existingBatch)).thenReturn(existingBatch);
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingBatch), anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return batchItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .sorted(Comparator.comparing(StockAiSuggestionItem::getRankNo))
                            .toList();
                });

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals(List.of("KO", "JNJ", "MSFT"), response.suggestedStocks().stream().map(stock -> stock.symbol()).toList());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, staleNvda.getStatus());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, latestKo.getStatus());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, expiredJnj.getStatus());
        assertEquals(StockAiSuggestionItemStatus.ACTIVE, expiredMsft.getStatus());
        assertEquals(List.of(1, 2, 3), response.suggestedStocks().stream().map(stock -> stock.rankNo()).toList());
        verify(itemRepository, times(3)).save(any(StockAiSuggestionItem.class));
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        assertEquals(
                response.suggestedStocks().size(),
                response.suggestedStocks().stream().map(stock -> stock.matchScore()).distinct().count()
        );
        response.suggestedStocks().forEach(stock -> {
            String detailReason = stock.detailReason().toLowerCase();
            assertTrue(detailReason.contains("risk category"));
            assertTrue(detailReason.contains("volatility"));
            assertTrue(detailReason.contains("trend"));
            assertTrue(detailReason.contains("price consistency"));
            assertTrue(detailReason.contains("volume trend"));
            assertTrue(detailReason.contains("data quality"));
        });
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
    void openAiSuccessUpdatesExistingSameInputFallbackCachedBatchWithoutDuplicateInsert() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingFallbackCached = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        existingFallbackCached.setSuggestionBatchId(95L);
        existingFallbackCached.setErrorMessage("previous fallback");
        existingFallbackCached.setPromptTokens(null);
        existingFallbackCached.setCompletionTokens(null);
        existingFallbackCached.setTotalTokens(null);
        StockAiSuggestionItem staleAggressiveItem = suggestionItem(existingFallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        staleAggressiveItem.setSuggestionItemId(96L);
        staleAggressiveItem.setSymbol("NVDA");
        staleAggressiveItem.setRiskLevel("aggressive");
        staleAggressiveItem.setAnalysisSnapshot(snapshot("NVDA"));
        List<StockAiSuggestionItem> batchItems = new ArrayList<>(List.of(staleAggressiveItem));

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString()))
                .thenReturn(Optional.of(existingFallbackCached));
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
                .thenReturn(List.of(staleAggressiveItem));
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingFallbackCached)).thenReturn(batchItems);
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() == existingFallbackCached
                    && item.getSuggestionItemId() == null
                    && item.getSymbol() != null) {
                item.setSuggestionItemId(97L);
                batchItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(eq(existingFallbackCached), anyCollection()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return batchItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse response = service.refreshSuggestionsForCurrentUser();

        assertEquals(95L, response.batchId());
        assertEquals("SUCCESS", response.batchStatus());
        assertFalse(response.fallbackUsed());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, existingFallbackCached.getStatus());
        assertNull(existingFallbackCached.getErrorMessage());
        assertEquals(180, existingFallbackCached.getTotalTokens());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, staleAggressiveItem.getStatus());
        assertEquals(List.of("MSFT"), response.suggestedStocks().stream().map(stock -> stock.symbol()).toList());
        verify(batchRepository).save(same(existingFallbackCached));
    }

    @Test
    void rejectsSteadyMovementWhenPriceConsistencyIsChoppy() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        googSnapshot.setTrend("volatile downtrend");
        googSnapshot.setPriceConsistency("choppy downward movement");
        String invalidJson = googSuggestionJson(
                "Observing steady movement",
                "GOOG appears steady with moderate risk and complete data.",
                "GOOG has risk category moderate, volatility moderate, trend is volatile downtrend, price consistency choppy downward movement, volume trend stable, and data quality complete, but the wording incorrectly says steady movement."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                OpenAiSuggestionResult.failure("retry unavailable")
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        JsonNode retryPrompt = readJson(promptCaptor.getAllValues().get(1));
        assertTrue(retryPrompt.path("previousValidationError").asText().contains("AI wording contradicts volatile or choppy snapshot data"));
        assertTrue(retryPrompt.path("retryConstraints").toString().contains("Do not use steady, smooth, consistent, stable, or clear trend wording"));
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
                        && "retry unavailable".equals(batch.getErrorMessage())
        ));
    }

    @Test
    void retryPromptUsesNaturalFactorGuidanceWhenAiOmitsInputFactors() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        String invalidJson = googSuggestionJson(
                "Risk-aligned paper-trading practice",
                "GOOG gives paper-trading practice with moderate risk and complete data.",
                "GOOG is included as an educational paper-trading example for beginner learning practice today."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, invalidJson, 100, 80, 180, "stop", null),
                OpenAiSuggestionResult.failure("retry unavailable")
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        JsonNode retryPrompt = readJson(promptCaptor.getAllValues().get(1));
        assertTrue(retryPrompt.path("previousValidationError").asText().contains("AI detail reason does not mention enough input factors"));
        String retryConstraints = retryPrompt.path("retryConstraints").toString();
        assertTrue(retryConstraints.contains("at least two concrete provided factors"));
        assertTrue(retryConstraints.contains("natural wording"));
        assertTrue(retryConstraints.contains("risk"));
        assertTrue(retryConstraints.contains("volume"));
        assertTrue(retryConstraints.contains("data quality"));
        assertFalse(retryConstraints.contains("exact factor phrases"));
    }

    @Test
    void detailReasonWithSemanticFactorsPassesWithoutExactVolumeTrendOrDataQuality() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        String validJson = googSuggestionJson(
                "Risk-aligned paper-trading practice",
                "GOOG gives paper-trading practice with moderate risk and complete data.",
                "GOOG has risk category moderate, volatility moderate, trend is strong uptrend, and price consistency smooth upward movement for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, validJson, 100, 80, 180, "stop", null)
        );

        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
        assertEquals(1, promptCaptor.getAllValues().size());
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS
                        && batch.getErrorMessage() == null
        ));
    }

    @Test
    void repeatedTrendLabelWordingIsSanitizedWithoutRetry() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        String validJson = googSuggestionJson(
                "Volatility comparison practice",
                "GOOG gives paper-trading practice with moderate risk and complete data.",
                "GOOG has risk category moderate, volatility moderate, strong uptrend trend, price consistency smooth upward movement, volume trend stable, and data quality complete for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, validJson, 100, 80, 180, "stop", null)
        );

        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
        assertEquals(1, promptCaptor.getAllValues().size());
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS
                        && batch.getErrorMessage() == null
        ));
        verify(itemRepository).save(argThat(item ->
                item.getDetailReason().contains("strong uptrend")
                        && !item.getDetailReason().contains("uptrend trend")
        ));
    }

    @Test
    void validDetailReasonWithGroundedFactorsPassesWithoutRetry() {
        StockAnalysisSnapshot googSnapshot = snapshot("GOOG");
        String validJson = googSuggestionJson(
                "Risk-aligned paper-trading practice",
                "GOOG gives paper-trading practice with moderate risk and complete data.",
                "GOOG has risk category moderate, volatility moderate, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete for educational paper-trading practice."
        );

        ArgumentCaptor<String> promptCaptor = refreshWithGoogOpenAiResults(
                googSnapshot,
                new OpenAiSuggestionResult(true, validJson, 100, 80, 180, "stop", null)
        );

        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
        assertEquals(1, promptCaptor.getAllValues().size());
        verify(batchRepository).save(argThat(batch ->
                batch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS
                        && batch.getErrorMessage() == null
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
                "GOOG has risk category moderate, volatility moderate, trend is volatile downtrend, price consistency uneven downward movement, volume trend stable, and data quality complete, but the wording incorrectly says smooth trend."
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
                "GOOG has risk category moderate, volatility moderate, trend is volatile downtrend, price consistency choppy downward movement, volume trend stable, and data quality complete, but the wording incorrectly says clear trend."
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
                "GOOG has risk category moderate, volatility moderate, trend is volatile downtrend, price consistency choppy downward movement, volume trend stable, and data quality complete, but the wording incorrectly says stable movement."
        );
        String validRetryJson = googSuggestionJson(
                "Choppy movement learning",
                "GOOG gives choppy paper-trading practice with moderate risk and complete data.",
                "GOOG has risk category moderate, volatility moderate, trend is volatile downtrend, price consistency choppy downward movement, volume trend stable, and data quality complete for educational paper-trading practice."
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
                "GOOG has risk category moderate, volatility moderate, trend is volatile downtrend, price consistency choppy downward movement, volume trend stable, and data quality complete for educational paper-trading practice."
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        fallbackCached.setPromptVersion("stock-suggestion-v3");
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        assertEquals("Microsoft has risk category moderate, volatility moderate, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete for beginner-friendly paper-trading practice.", savedMsft.getDetailReason());
        assertEquals(snapshot("MSFT").getAnalysisSnapshotId(), savedMsft.getAnalysisSnapshot().getAnalysisSnapshotId());
        verify(batchRepository, times(1)).save(same(savedSuccessBatch.get()));
        verify(openAiClient, times(1)).generateSuggestion(anyString(), anyString());
    }

    @Test
    void existingFallbackCachedSameInputHashCreatesNewRuleBasedBatchAndPreservesFallbackHistory() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch fallbackCached = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        fallbackCached.setSuggestionBatchId(95L);
        fallbackCached.setPromptVersion("stock-suggestion-v3");
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
    void generateSuggestionsForUser_shiftsPersonalizationFromOnboardingToBehaviorAsBehaviorConfidenceIncreases() throws Exception {
        UserInvestmentProfile aggressiveProfile = profile();
        aggressiveProfile.setRiskTolerance(RiskTolerance.AGGRESSIVE);
        aggressiveProfile.setInvestmentGoal(InvestmentGoal.GROWTH);
        aggressiveProfile.setPreferredVolatility(PreferredVolatility.HIGH);
        aggressiveProfile.setBehaviorConfidence(BehaviorConfidence.HIGH);
        aggressiveProfile.setBehaviorStyle(BehaviorStyle.SPECULATIVE);
        aggressiveProfile.setBehaviorRiskScore(95);

        BehaviorSummaryForSuggestion mediumConservativeBehavior = behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 10, 0),
                31L,
                BehaviorConfidence.MEDIUM,
                UserBehaviorStyle.CONSERVATIVE,
                28,
                "conservative",
                "KO,JNJ"
        );
        BehaviorSummaryForSuggestion highConservativeBehavior = behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 11, 0),
                31L,
                BehaviorConfidence.HIGH,
                UserBehaviorStyle.ACTIVE_TRADER,
                29,
                "conservative",
                "KO,JNJ,MSFT",
                HighVolatilityExposure.LOW,
                36,
                35
        );
        List<StockAiSuggestionBatch> savedBatches = new ArrayList<>();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L))
                .thenReturn(Optional.of(aggressiveProfile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D")))
                .thenAnswer(invocation -> adaptiveSnapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v3"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.empty());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L))
                .thenReturn(null)
                .thenReturn(mediumConservativeBehavior)
                .thenReturn(highConservativeBehavior);
        when(openAiClient.generateSuggestion(anyString(), anyString()))
                .thenReturn(
                        openAiSuccess(adaptiveSuggestionJson(
                                "TSLA",
                                "aggressive",
                                "Aggressive onboarding practice",
                                "Tesla is shown because your declared onboarding profile is aggressive and growth focused.",
                                "Tesla has risk category aggressive, volatility high, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. It fits the declared onboarding profile for educational paper-trading practice. No paper-trading behavior profile is available yet, so observed behavior remains a small secondary signal rather than the main driver."
                        )),
                        openAiSuccess(adaptiveTwoSuggestionJson(
                                "KO",
                                "conservative",
                                "Lower-risk behavior practice",
                                "Coca-Cola is shown because medium-confidence paper-trading behavior now supports lower-risk practice.",
                                "Coca-Cola has risk category conservative, volatility low, trend is steady uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. Your onboarding profile is still aggressive, but your observed paper-trading behavior is now medium confidence and leans conservative, so this remains a mixed educational paper-trading example.",
                                "MSFT",
                                "moderate",
                                "Moderate bridge practice",
                                "Microsoft keeps the medium-confidence suggestion set mixed for educational paper-trading practice.",
                                "Microsoft has risk category moderate, volatility medium, trend is steady uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. It bridges the declared aggressive onboarding profile and the observed conservative paper-trading behavior without presenting real-money investment instructions."
                        )),
                        openAiSuccess(adaptiveTwoSuggestionJson(
                                "JNJ",
                                "conservative",
                                "Behavior-dominant learning",
                                "Johnson & Johnson is shown because high-confidence paper-trading behavior now carries more weight.",
                                "Johnson & Johnson has risk category conservative, volatility low, trend is steady uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. Although your declared onboarding profile is aggressive, your observed paper-trading behavior shows repeated lower-risk practice, so this behavior-dominant suggestion explains the conflict in beginner-friendly educational terms.",
                                "MSFT",
                                "moderate",
                                "Moderate behavior bridge",
                                "Microsoft provides a moderate bridge between aggressive onboarding and lower-risk observed behavior.",
                                "Microsoft has risk category moderate, volatility medium, trend is steady uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. It keeps the high-confidence behavior stage educational while acknowledging both declared onboarding preference and observed paper-trading behavior."
                        ))
                );
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId((long) savedBatches.size() + 200L);
            savedBatches.add(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            item.setSuggestionItemId((long) savedItems.size() + 300L);
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems.stream()
                        .filter(item -> item.getSuggestionBatch() == invocation.getArgument(0))
                        .toList());

        StockAiSuggestionResponse stageAResponse = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );
        StockAiSuggestionResponse stageBResponse = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );
        StockAiSuggestionResponse stageCResponse = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient, times(3)).generateSuggestion(anyString(), promptCaptor.capture());
        List<JsonNode> prompts = promptCaptor.getAllValues().stream()
                .map(this::readJson)
                .toList();
        JsonNode stageA = prompts.get(0);
        JsonNode stageB = prompts.get(1);
        JsonNode stageC = prompts.get(2);

        assertAdaptivePromptShape(stageA);
        assertAdaptivePromptShape(stageB);
        assertAdaptivePromptShape(stageC);
        assertEquals("AGGRESSIVE", stageA.path("declaredOnboardingProfile").path("riskTolerance").asText());
        assertEquals("GROWTH", stageA.path("declaredOnboardingProfile").path("investmentGoal").asText());
        assertEquals("HIGH", stageA.path("declaredOnboardingProfile").path("preferredVolatility").asText());

        assertFalse(stageA.path("observedPaperTradingBehavior").path("hasBehaviorProfile").asBoolean());
        assertEquals("LOW", stageA.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals(80, stageA.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(20, stageA.path("personalizationWeight").path("behaviorWeight").asInt());
        assertCandidateEvidence(stageA, "TSLA", "MATCH", "INSUFFICIENT_DATA");
        assertTrue(signalFor(stageA, "TSLA").path("combinedFitScore").asInt()
                > signalFor(stageA, "KO").path("combinedFitScore").asInt());

        assertTrue(stageB.path("observedPaperTradingBehavior").path("hasBehaviorProfile").asBoolean());
        assertEquals("MEDIUM", stageB.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals("conservative", stageB.path("observedPaperTradingBehavior").path("favoriteRiskCategory").asText());
        assertEquals("KO,JNJ", stageB.path("observedPaperTradingBehavior").path("mostTradedSymbols").asText());
        assertEquals(40, stageB.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(60, stageB.path("personalizationWeight").path("behaviorWeight").asInt());
        assertCandidateEvidence(stageB, "KO", "PARTIAL_MATCH", "PARTIAL_MATCH");
        assertTrue(signalFor(stageB, "KO").path("behaviorFitScore").asInt()
                > signalFor(stageB, "TSLA").path("behaviorFitScore").asInt());
        assertTrue(signalFor(stageB, "KO").path("combinedFitScore").asInt()
                > signalFor(stageA, "KO").path("combinedFitScore").asInt());

        assertEquals("HIGH", stageC.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals("ACTIVE_TRADER", stageC.path("observedPaperTradingBehavior").path("behaviorStyle").asText());
        assertEquals("KO,JNJ,MSFT", stageC.path("observedPaperTradingBehavior").path("mostTradedSymbols").asText());
        assertEquals("LOW", stageC.path("observedPaperTradingBehavior").path("highVolatilityExposure").asText());
        assertEquals(10, stageC.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(90, stageC.path("personalizationWeight").path("behaviorWeight").asInt());
        assertCandidateEvidence(stageC, "JNJ", "PARTIAL_MATCH", "MATCH");
        assertEquals("MISMATCH", signalFor(stageC, "TSLA").path("behaviorCompatibility").asText());
        assertEquals("MISMATCH", signalFor(stageC, "NVDA").path("behaviorCompatibility").asText());
        assertEquals("MISMATCH", signalFor(stageC, "AMD").path("behaviorCompatibility").asText());
        assertTrue(signalFor(stageC, "JNJ").path("combinedFitScore").asInt()
                > signalFor(stageC, "TSLA").path("combinedFitScore").asInt());
        assertTrue(signalFor(stageC, "JNJ").path("combinedFitScore").asInt()
                >= signalFor(stageB, "JNJ").path("combinedFitScore").asInt());

        assertEquals(3, savedBatches.size());
        assertEquals("stock-suggestion-v3", savedBatches.get(0).getPromptVersion());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, savedBatches.get(0).getStatus());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, savedBatches.get(1).getStatus());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, savedBatches.get(2).getStatus());
        assertNotNull(savedBatches.get(0).getInputHash());
        assertFalse(savedBatches.get(0).getInputHash().isBlank());
        assertNotEquals(savedBatches.get(0).getInputHash(), savedBatches.get(1).getInputHash());
        assertNotEquals(savedBatches.get(1).getInputHash(), savedBatches.get(2).getInputHash());
        assertNotEquals(savedBatches.get(0).getInputHash(), savedBatches.get(2).getInputHash());

        assertFalse(stageAResponse.fallbackUsed());
        assertFalse(stageBResponse.fallbackUsed());
        assertFalse(stageCResponse.fallbackUsed());
        assertEquals("SUCCESS", stageAResponse.batchStatus());
        assertEquals("SUCCESS", stageBResponse.batchStatus());
        assertEquals("SUCCESS", stageCResponse.batchStatus());
        assertResponseDoesNotExposeRawAiData(stageCResponse);
        assertTrue(savedItems.stream().anyMatch(item -> item.getSymbol().equals("JNJ")
                && item.getShortReason().toLowerCase().contains("paper-trading behavior")
                && item.getDetailReason().toLowerCase().contains("declared onboarding profile")
                && item.getDetailReason().toLowerCase().contains("observed paper-trading behavior")));

        verify(behaviorProfileService, times(3)).getBehaviorSummaryForSuggestion(1L);
        verify(behaviorProfileService, never()).createLowConfidenceProfileIfMissing(any());
    }

    @Test
    void generateSuggestionsForUser_retriesAllAggressiveOutputWhenHighConfidenceBehaviorIsLowerRisk() {
        UserInvestmentProfile aggressiveProfile = profile();
        aggressiveProfile.setRiskTolerance(RiskTolerance.AGGRESSIVE);
        aggressiveProfile.setInvestmentGoal(InvestmentGoal.GROWTH);
        aggressiveProfile.setPreferredVolatility(PreferredVolatility.HIGH);
        aggressiveProfile.setBehaviorConfidence(BehaviorConfidence.HIGH);
        aggressiveProfile.setBehaviorStyle(BehaviorStyle.SPECULATIVE);
        aggressiveProfile.setBehaviorRiskScore(95);
        BehaviorSummaryForSuggestion highActiveLowerRiskBehavior = behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 11, 0),
                31L,
                BehaviorConfidence.HIGH,
                UserBehaviorStyle.ACTIVE_TRADER,
                29,
                "conservative",
                "KO,JNJ,MSFT",
                HighVolatilityExposure.LOW,
                36,
                35
        );
        List<StockAiSuggestionBatch> savedBatches = new ArrayList<>();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L))
                .thenReturn(Optional.of(aggressiveProfile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D")))
                .thenAnswer(invocation -> adaptiveSnapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                eq(1L),
                eq("gpt-4o-mini"),
                eq("stock-suggestion-v3"),
                anyString(),
                anyCollection()
        )).thenReturn(Optional.empty());
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(highActiveLowerRiskBehavior);
        when(openAiClient.generateSuggestion(anyString(), anyString()))
                .thenReturn(
                        openAiSuccess(allAggressiveSuggestionJson()),
                        openAiSuccess(adaptiveTwoSuggestionJson(
                                "JNJ",
                                "conservative",
                                "Behavior-dominant learning",
                                "Johnson & Johnson is shown because high-confidence paper-trading behavior now carries more weight.",
                                "Johnson & Johnson has risk category conservative, volatility low, trend is steady uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. Although your declared onboarding profile is aggressive, your observed paper-trading behavior shows repeated lower-risk practice, so this behavior-dominant suggestion explains the conflict in beginner-friendly educational terms.",
                                "MSFT",
                                "moderate",
                                "Moderate behavior bridge",
                                "Microsoft provides a moderate bridge between aggressive onboarding and lower-risk observed behavior.",
                                "Microsoft has risk category moderate, volatility medium, trend is steady uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. It keeps the high-confidence behavior stage educational while acknowledging both declared onboarding preference and observed paper-trading behavior."
                        ))
                );
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId((long) savedBatches.size() + 260L);
            savedBatches.add(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            item.setSuggestionItemId((long) savedItems.size() + 360L);
            savedItems.add(item);
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> savedItems.stream()
                        .filter(item -> item.getSuggestionBatch() == invocation.getArgument(0))
                        .toList());

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        assertEquals("SUCCESS", response.batchStatus());
        assertFalse(response.fallbackUsed());
        assertEquals(1, savedBatches.size());
        assertEquals(StockAiSuggestionBatchStatus.SUCCESS, savedBatches.get(0).getStatus());
        assertTrue(savedItems.stream().anyMatch(item -> item.getSymbol().equals("JNJ")));
        assertFalse(savedItems.stream().allMatch(item -> List.of("NVDA", "TSLA", "AMD").contains(item.getSymbol())));
        verify(behaviorProfileService).getBehaviorSummaryForSuggestion(1L);
        verify(behaviorProfileService, never()).createLowConfidenceProfileIfMissing(any());
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
    void behaviorRiskSignalsStillInfluenceCandidatesWhenFavoriteAndStyleAreMissing() throws Exception {
        String userContent = promptForBehaviorSummary(behaviorSummary(
                LocalDateTime.of(2026, 6, 8, 9, 0),
                42L,
                BehaviorConfidence.HIGH,
                null,
                null,
                null,
                null,
                null,
                82,
                null
        ));

        JsonNode root = new ObjectMapper().readTree(userContent);

        assertEquals("HIGH", root.path("observedPaperTradingBehavior").path("behaviorConfidence").asText());
        assertEquals(10, root.path("personalizationWeight").path("onboardingWeight").asInt());
        assertEquals(90, root.path("personalizationWeight").path("behaviorWeight").asInt());
        assertEquals("MATCH", signalFor(root, "TSLA").path("behaviorCompatibility").asText());
        assertEquals("MATCH", signalFor(root, "NVDA").path("behaviorCompatibility").asText());
        assertEquals("MATCH", signalFor(root, "AMD").path("behaviorCompatibility").asText());
        assertEquals("PARTIAL_MATCH", signalFor(root, "MSFT").path("behaviorCompatibility").asText());
        assertEquals("MISMATCH", signalFor(root, "KO").path("behaviorCompatibility").asText());
        assertEquals("MISMATCH", signalFor(root, "JNJ").path("behaviorCompatibility").asText());
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
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
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
    void openAiFailureWithCachedSuccessCreatesRuleBasedCooldownBatchAndSecondRefreshDoesNotCallOpenAiAgain() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch cachedSuccess = batch(profile, StockAiSuggestionBatchStatus.SUCCESS);
        cachedSuccess.setSuggestionBatchId(70L);
        cachedSuccess.setCreatedAt(LocalDateTime.now().minusDays(1));
        cachedSuccess.setUpdatedAt(LocalDateTime.now().minusDays(1));
        cachedSuccess.setExpiresAt(LocalDateTime.now().plusHours(2));
        StockAiSuggestionItem cachedItem = suggestionItem(cachedSuccess, StockAiSuggestionItemStatus.ACTIVE);
        cachedItem.setAnalysisSnapshot(snapshot("MSFT"));
        AtomicReference<StockAiSuggestionBatch> savedRuleBased = new AtomicReference<>();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenAnswer(invocation -> savedRuleBased.get() == null
                        ? Optional.of(cachedSuccess)
                        : Optional.of(savedRuleBased.get()));
        when(batchRepository.findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(eq(1L), eq(StockAiSuggestionTriggerReason.MANUAL_REFRESH)))
                .thenAnswer(invocation -> Optional.ofNullable(savedRuleBased.get()));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of(cachedItem));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batch.setSuggestionBatchId(71L);
            savedRuleBased.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() != cachedSuccess) {
                item.setSuggestionItemId((long) savedItems.size() + 80L);
                savedItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    if (batch == cachedSuccess) {
                        return List.of(cachedItem).stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                    }
                    return savedItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse first = service.refreshSuggestionsForCurrentUser();
        StockAiSuggestionResponse second = service.refreshSuggestionsForCurrentUser();

        assertEquals("FALLBACK_RULE_BASED", first.batchStatus());
        assertTrue(first.fallbackUsed());
        assertFalse(first.refreshAllowed());
        assertNotNull(first.nextRefreshAllowedAt());
        assertEquals(71L, first.batchId());
        assertEquals(first.batchId(), second.batchId());
        assertEquals("FALLBACK_RULE_BASED", second.batchStatus());
        assertFalse(second.refreshAllowed());
        assertEquals(first.nextRefreshAllowedAt(), second.nextRefreshAllowedAt());
        assertEquals(3, savedItems.size());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, savedRuleBased.get().getStatus());
        assertEquals("OpenAI unavailable", savedRuleBased.get().getErrorMessage());
        assertTrue(savedItems.stream().allMatch(item -> item.getSuggestionBatch() == savedRuleBased.get()));
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, cachedItem.getStatus());
        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
        verify(batchRepository, times(1)).save(any(StockAiSuggestionBatch.class));
        verify(itemRepository).save(cachedItem);
    }

    @Test
    void repeatedOpenAiFailureWithSameInputHashReusesExistingRuleBasedBatch() {
        UserInvestmentProfile profile = profile();
        AtomicReference<StockAiSuggestionBatch> savedRuleBased = new AtomicReference<>();
        List<StockAiSuggestionItem> fallbackItems = new ArrayList<>();
        int[] batchSaveCount = {0};

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenAnswer(invocation -> Optional.ofNullable(savedRuleBased.get()));
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            batchSaveCount[0]++;
            if (batch.getSuggestionBatchId() == null) {
                batch.setSuggestionBatchId(71L);
            }
            savedRuleBased.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() == savedRuleBased.get()
                    && item.getSuggestionItemId() == null) {
                item.setSuggestionItemId((long) fallbackItems.size() + 80L);
                fallbackItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    return fallbackItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse first = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );
        StockAiSuggestionResponse second = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals("FALLBACK_RULE_BASED", first.batchStatus());
        assertEquals("FALLBACK_RULE_BASED", second.batchStatus());
        assertEquals(first.batchId(), second.batchId());
        assertEquals(71L, second.batchId());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, savedRuleBased.get().getStatus());
        assertEquals(3, fallbackItems.size());
        assertTrue(fallbackItems.stream().allMatch(item -> item.getStatus() == StockAiSuggestionItemStatus.ACTIVE));
        assertEquals(1, batchSaveCount[0]);
        verify(batchRepository, times(1)).save(same(savedRuleBased.get()));
        verify(openAiClient, times(2)).generateSuggestion(anyString(), anyString());
    }

    @Test
    void existingFallbackCachedSameInputHashIsUpdatedToRuleBasedAfterOpenAiFailure() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingFallbackCached = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        existingFallbackCached.setSuggestionBatchId(71L);
        existingFallbackCached.setErrorMessage("previous failure");
        LocalDateTime oldUpdatedAt = LocalDateTime.now().minusHours(3);
        LocalDateTime oldExpiresAt = LocalDateTime.now().plusHours(1);
        existingFallbackCached.setUpdatedAt(oldUpdatedAt);
        existingFallbackCached.setExpiresAt(oldExpiresAt);
        StockAiSuggestionItem existingFallbackItem = suggestionItem(existingFallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        existingFallbackItem.setSuggestionItemId(81L);
        existingFallbackItem.setAnalysisSnapshot(snapshot("MSFT"));
        AtomicReference<StockAiSuggestionBatch> savedRuleBased = new AtomicReference<>();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString()))
                .thenReturn(Optional.of(existingFallbackCached));
        when(openAiClient.generateSuggestion(anyString(), anyString())).thenReturn(OpenAiSuggestionResult.failure("OpenAI unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE)).thenReturn(List.of(existingFallbackItem));
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingFallbackCached)).thenReturn(List.of(existingFallbackItem));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            if (batch.getSuggestionBatchId() == null) {
                batch.setSuggestionBatchId(72L);
            }
            savedRuleBased.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() == savedRuleBased.get() && item.getSymbol() != null) {
                item.setSuggestionItemId((long) savedItems.size() + 90L);
                savedItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    if (batch == existingFallbackCached) {
                        List<StockAiSuggestionItem> items = new ArrayList<>();
                        items.add(existingFallbackItem);
                        items.addAll(savedItems);
                        return items.stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                    }
                    return savedItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals(71L, response.batchId());
        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        assertEquals("OpenAI unavailable", existingFallbackCached.getErrorMessage());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, existingFallbackCached.getStatus());
        assertTrue(existingFallbackCached.getUpdatedAt().isAfter(oldUpdatedAt));
        assertTrue(existingFallbackCached.getExpiresAt().isAfter(oldExpiresAt));
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, savedRuleBased.get().getStatus());
        assertEquals("OpenAI unavailable", savedRuleBased.get().getErrorMessage());
        assertEquals(3, savedItems.size());
        assertTrue(savedItems.stream().allMatch(item -> item.getSuggestionBatch() == existingFallbackCached));
        assertTrue(savedItems.stream().map(StockAiSuggestionItem::getSymbol).toList().contains("MSFT"));
        verify(batchRepository).save(same(existingFallbackCached));
    }

    @Test
    void existingRuleBasedSameInputHashIsReusedBeforeOpenAiCall() {
        UserInvestmentProfile profile = profile();
        StockAiSuggestionBatch existingRuleBased = batch(profile, StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        existingRuleBased.setSuggestionBatchId(72L);
        StockAiSuggestionItem existingRuleBasedItem = suggestionItem(existingRuleBased, StockAiSuggestionItemStatus.ACTIVE);
        existingRuleBasedItem.setSuggestionItemId(82L);
        existingRuleBasedItem.setAnalysisSnapshot(snapshot("MSFT"));

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(profile));
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenReturn(Optional.of(existingRuleBased));
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenReturn(List.of(existingRuleBasedItem));

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals(72L, response.batchId());
        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        verify(openAiClient, never()).generateSuggestion(anyString(), anyString());
        verify(batchRepository, never()).save(any());
        verify(itemRepository, never()).findByUserUserIdAndStatus(anyLong(), any());
    }

    @Test
    void openAiValidationFailureWithSameInputFallbackCachedUpdatesToCurrentRuleBasedFallback() {
        UserInvestmentProfile aggressiveProfile = profile();
        aggressiveProfile.setRiskTolerance(RiskTolerance.AGGRESSIVE);
        aggressiveProfile.setInvestmentGoal(InvestmentGoal.GROWTH);
        aggressiveProfile.setPreferredVolatility(PreferredVolatility.HIGH);
        BehaviorSummaryForSuggestion highConservativeBehavior = behaviorSummary(
                LocalDateTime.now().minusMinutes(1),
                31L,
                BehaviorConfidence.HIGH,
                UserBehaviorStyle.ACTIVE_TRADER,
                29,
                "conservative",
                "KO,JNJ,MSFT",
                HighVolatilityExposure.LOW,
                36,
                22
        );
        StockAiSuggestionBatch staleFallbackCached = batch(aggressiveProfile, StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        staleFallbackCached.setSuggestionBatchId(45L);
        staleFallbackCached.setInputHash("current-input-hash");
        staleFallbackCached.setExpiresAt(LocalDateTime.now().plusHours(2));
        StockAiSuggestionItem staleNvda = suggestionItem(staleFallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        staleNvda.setSymbol("NVDA");
        staleNvda.setRankNo(1);
        staleNvda.setRiskLevel("aggressive");
        staleNvda.setAnalysisSnapshot(adaptiveSnapshot("NVDA"));
        StockAiSuggestionItem staleTsla = suggestionItem(staleFallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        staleTsla.setSymbol("TSLA");
        staleTsla.setRankNo(2);
        staleTsla.setRiskLevel("aggressive");
        staleTsla.setAnalysisSnapshot(adaptiveSnapshot("TSLA"));
        StockAiSuggestionItem staleAmd = suggestionItem(staleFallbackCached, StockAiSuggestionItemStatus.ACTIVE);
        staleAmd.setSymbol("AMD");
        staleAmd.setRankNo(3);
        staleAmd.setRiskLevel("aggressive");
        staleAmd.setAnalysisSnapshot(adaptiveSnapshot("AMD"));
        AtomicReference<StockAiSuggestionBatch> savedRuleBased = new AtomicReference<>();
        List<StockAiSuggestionItem> savedItems = new ArrayList<>();

        when(profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(1L)).thenReturn(Optional.of(aggressiveProfile));
        when(behaviorProfileService.getBehaviorSummaryForSuggestion(1L)).thenReturn(highConservativeBehavior);
        when(batchRepository.findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), anyCollection(), any()))
                .thenReturn(Optional.of(staleFallbackCached));
        when(stockAnalysisService.createOrReuseSnapshot(anyString(), eq("7D"))).thenAnswer(invocation -> adaptiveSnapshot(invocation.getArgument(0)));
        when(openAiClient.getModel()).thenReturn("gpt-4o-mini");
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(eq(1L), eq("gpt-4o-mini"), eq("stock-suggestion-v3"), anyString()))
                .thenReturn(Optional.of(staleFallbackCached));
        when(openAiClient.generateSuggestion(anyString(), anyString()))
                .thenReturn(new OpenAiSuggestionResult(true, allAggressiveSuggestionJson(), 100, 80, 180, "stop", null))
                .thenReturn(OpenAiSuggestionResult.failure("retry unavailable"));
        when(itemRepository.findByUserUserIdAndStatus(1L, StockAiSuggestionItemStatus.ACTIVE))
                .thenReturn(List.of(staleNvda, staleTsla, staleAmd));
        when(itemRepository.findBySuggestionBatchOrderByRankNoAsc(staleFallbackCached))
                .thenReturn(List.of(staleNvda, staleTsla, staleAmd));
        when(batchRepository.save(any(StockAiSuggestionBatch.class))).thenAnswer(invocation -> {
            StockAiSuggestionBatch batch = invocation.getArgument(0);
            if (batch.getSuggestionBatchId() == null) {
                batch.setSuggestionBatchId(76L);
            }
            savedRuleBased.set(batch);
            return batch;
        });
        when(itemRepository.save(any(StockAiSuggestionItem.class))).thenAnswer(invocation -> {
            StockAiSuggestionItem item = invocation.getArgument(0);
            if (item.getSuggestionBatch() == savedRuleBased.get() && item.getSymbol() != null) {
                item.setSuggestionItemId((long) savedItems.size() + 90L);
                savedItems.add(item);
            }
            return item;
        });
        when(itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(any(), anyCollection()))
                .thenAnswer(invocation -> {
                    StockAiSuggestionBatch batch = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<StockAiSuggestionItemStatus> statuses = new ArrayList<>((java.util.Collection<StockAiSuggestionItemStatus>) invocation.getArgument(1));
                    if (batch == staleFallbackCached) {
                        List<StockAiSuggestionItem> items = new ArrayList<>();
                        items.add(staleNvda);
                        items.add(staleTsla);
                        items.add(staleAmd);
                        items.addAll(savedItems);
                        return items.stream()
                                .filter(item -> statuses.contains(item.getStatus()))
                                .toList();
                    }
                    return savedItems.stream()
                            .filter(item -> statuses.contains(item.getStatus()))
                            .toList();
                });

        StockAiSuggestionResponse response = service.generateSuggestionsForUser(
                user,
                StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                false
        );

        assertEquals("FALLBACK_RULE_BASED", response.batchStatus());
        assertTrue(response.fallbackUsed());
        assertEquals(45L, response.batchId());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, staleFallbackCached.getStatus());
        assertEquals(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED, savedRuleBased.get().getStatus());
        assertEquals("retry unavailable", savedRuleBased.get().getErrorMessage());
        List<StockAiSuggestionItem> activeSavedItems = savedItems.stream()
                .filter(item -> item.getStatus() == StockAiSuggestionItemStatus.ACTIVE
                        || item.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED)
                .toList();
        assertEquals(List.of("KO", "JNJ", "MSFT"), activeSavedItems.stream().map(StockAiSuggestionItem::getSymbol).toList());
        assertTrue(activeSavedItems.stream().map(StockAiSuggestionItem::getRiskLevel).noneMatch("aggressive"::equals));
        assertTrue(activeSavedItems.stream().allMatch(item -> item.getSuggestionBatch() == staleFallbackCached));
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, staleNvda.getStatus());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, staleTsla.getStatus());
        assertEquals(StockAiSuggestionItemStatus.EXPIRED, staleAmd.getStatus());
        verify(batchRepository).save(same(staleFallbackCached));
        verify(batchRepository, never()).findTopByUserUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(1L),
                eq(StockAiSuggestionBatchStatus.SUCCESS),
                any()
        );
        verify(batchRepository, never()).findTopByUserUserIdAndStatusOrderByCreatedAtDesc(1L, StockAiSuggestionBatchStatus.SUCCESS);
        verify(behaviorProfileService).getBehaviorSummaryForSuggestion(1L);
        verifyNoMoreInteractions(behaviorProfileService);
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
                eq("stock-suggestion-v3"),
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
                eq("stock-suggestion-v3"),
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
        batch.setPromptVersion("stock-suggestion-v3");
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
        return behaviorSummary(
                updatedAt,
                behaviorProfileId,
                confidence,
                style,
                behaviorRiskScore,
                favoriteRiskCategory,
                mostTradedSymbols,
                null,
                behaviorRiskScore,
                behaviorRiskScore
        );
    }

    private BehaviorSummaryForSuggestion behaviorSummary(
            LocalDateTime updatedAt,
            Long behaviorProfileId,
            BehaviorConfidence confidence,
            UserBehaviorStyle style,
            Integer behaviorRiskScore,
            String favoriteRiskCategory,
            String mostTradedSymbols,
            HighVolatilityExposure highVolatilityExposure,
            Integer stockRiskExposureScore,
            Integer volatilityExposureScore
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
                highVolatilityExposure,
                stockRiskExposureScore,
                null,
                null,
                null,
                volatilityExposureScore,
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

    private StockAiSuggestionItem expiredItem(
            StockAiSuggestionBatch batch,
            Long itemId,
            String symbol,
            int rankNo,
            LocalDateTime updatedAt
    ) {
        StockAiSuggestionItem item = suggestionItem(batch, StockAiSuggestionItemStatus.EXPIRED);
        item.setSuggestionItemId(itemId);
        item.setSymbol(symbol);
        item.setRankNo(rankNo);
        item.setRiskLevel(risk(symbol));
        item.setAnalysisSnapshot(snapshot(symbol));
        item.setCreatedAt(updatedAt);
        item.setUpdatedAt(updatedAt);
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
                      "detailReason": "Microsoft has risk category moderate, volatility moderate, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete for beginner-friendly paper-trading practice."
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
                      "detailReason": "Although your onboarding profile was conservative, your paper-trading behavior shows higher risk tolerance. AMD has risk category aggressive, volatility moderate, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete, so it is presented as an educational paper-trading example rather than real-money advice."
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

    private OpenAiSuggestionResult openAiSuccess(String content) {
        return new OpenAiSuggestionResult(
                true,
                content,
                120,
                90,
                210,
                "stop",
                null
        );
    }

    private String adaptiveSuggestionJson(
            String symbol,
            String riskLevel,
            String suggestionLabel,
            String shortReason,
            String detailReason
    ) {
        return """
                {
                  "batchSummary": "Adaptive paper-trading suggestions are based on declared onboarding preferences and observed behavior evidence.",
                  "suggestedStocks": [
                    {
                      "symbol": "%s",
                      "rankNo": 1,
                      "matchScore": 78,
                      "riskLevel": "%s",
                      "suggestionLabel": "%s",
                      "shortReason": "%s",
                      "detailReason": "%s"
                    }
                  ]
                }
                """.formatted(symbol, riskLevel, suggestionLabel, shortReason, detailReason);
    }

    private String adaptiveTwoSuggestionJson(
            String firstSymbol,
            String firstRiskLevel,
            String firstSuggestionLabel,
            String firstShortReason,
            String firstDetailReason,
            String secondSymbol,
            String secondRiskLevel,
            String secondSuggestionLabel,
            String secondShortReason,
            String secondDetailReason
    ) {
        return """
                {
                  "batchSummary": "Adaptive paper-trading suggestions are based on declared onboarding preferences and observed behavior evidence.",
                  "suggestedStocks": [
                    {
                      "symbol": "%s",
                      "rankNo": 1,
                      "matchScore": 78,
                      "riskLevel": "%s",
                      "suggestionLabel": "%s",
                      "shortReason": "%s",
                      "detailReason": "%s"
                    },
                    {
                      "symbol": "%s",
                      "rankNo": 2,
                      "matchScore": 74,
                      "riskLevel": "%s",
                      "suggestionLabel": "%s",
                      "shortReason": "%s",
                      "detailReason": "%s"
                    }
                  ]
                }
                """.formatted(
                firstSymbol,
                firstRiskLevel,
                firstSuggestionLabel,
                firstShortReason,
                firstDetailReason,
                secondSymbol,
                secondRiskLevel,
                secondSuggestionLabel,
                secondShortReason,
                secondDetailReason
        );
    }

    private String allAggressiveSuggestionJson() {
        return """
                {
                  "batchSummary": "Aggressive-only paper-trading suggestions are intentionally invalid for this high-confidence lower-risk behavior test.",
                  "suggestedStocks": [
                    {
                      "symbol": "NVDA",
                      "rankNo": 1,
                      "matchScore": 78,
                      "riskLevel": "aggressive",
                      "suggestionLabel": "Aggressive onboarding example",
                      "shortReason": "NVIDIA is shown from declared aggressive onboarding preference with stored data evidence.",
                      "detailReason": "NVIDIA has risk category aggressive, volatility high, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. The suggestion follows declared aggressive onboarding preference for educational paper-trading practice, while observed paper-trading behavior still needs clearer balance in the overall set."
                    },
                    {
                      "symbol": "TSLA",
                      "rankNo": 2,
                      "matchScore": 76,
                      "riskLevel": "aggressive",
                      "suggestionLabel": "Growth profile practice",
                      "shortReason": "Tesla is shown from declared aggressive onboarding preference with stored data evidence.",
                      "detailReason": "Tesla has risk category aggressive, volatility high, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. It matches the declared growth onboarding profile for educational paper-trading practice, but the set does not properly reflect observed lower-risk paper-trading behavior."
                    },
                    {
                      "symbol": "AMD",
                      "rankNo": 3,
                      "matchScore": 74,
                      "riskLevel": "aggressive",
                      "suggestionLabel": "High-volatility practice",
                      "shortReason": "AMD is shown from declared aggressive onboarding preference with stored data evidence.",
                      "detailReason": "AMD has risk category aggressive, volatility high, trend is strong uptrend, price consistency smooth upward movement, volume trend stable, and data quality complete. It supports the declared aggressive onboarding profile for educational paper-trading practice, while observed lower-risk behavior is underrepresented in this generated set."
                    }
                  ]
                }
                """;
    }

    private JsonNode readJson(String value) {
        try {
            return new ObjectMapper().readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Expected valid JSON prompt input", e);
        }
    }

    private void assertAdaptivePromptShape(JsonNode root) {
        assertTrue(root.has("declaredOnboardingProfile"));
        assertTrue(root.has("observedPaperTradingBehavior"));
        assertTrue(root.has("personalizationWeight"));
        assertTrue(root.has("candidateFitSignals"));
        assertTrue(root.has("stockSnapshots"));
        assertTrue(root.has("beginnerSafetyRules"));
        assertEquals(8, root.path("candidateFitSignals").size());
        assertEquals(8, root.path("stockSnapshots").size());
    }

    private void assertCandidateEvidence(
            JsonNode root,
            String symbol,
            String expectedRiskCompatibility,
            String expectedBehaviorCompatibility
    ) {
        JsonNode signal = signalFor(root, symbol);
        assertEquals(symbol, signal.path("symbol").asText());
        assertEquals(expectedRiskCompatibility, signal.path("riskCompatibility").asText());
        assertEquals(expectedBehaviorCompatibility, signal.path("behaviorCompatibility").asText());
        assertTrue(signal.has("onboardingFitScore"));
        assertTrue(signal.has("behaviorFitScore"));
        assertTrue(signal.has("combinedFitScore"));
        assertTrue(signal.has("snapshotHash"));
    }

    private JsonNode signalFor(JsonNode root, String symbol) {
        for (JsonNode signal : root.path("candidateFitSignals")) {
            if (symbol.equals(signal.path("symbol").asText())) {
                return signal;
            }
        }
        return fail("Missing candidate fit signal for " + symbol);
    }

    private void assertResponseDoesNotExposeRawAiData(StockAiSuggestionResponse response) {
        String responseText = response.toString().toLowerCase();
        assertFalse(responseText.contains("declaredonboardingprofile"));
        assertFalse(responseText.contains("candidatefitsignals"));
        assertFalse(responseText.contains("raw"));
        assertFalse(responseText.contains("api key"));
        assertFalse(responseText.contains("fingerprint"));
        assertFalse(responseText.contains("service tier"));
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
                eq("stock-suggestion-v3"),
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

    private StockAnalysisSnapshot adaptiveSnapshot(String symbol) {
        StockAnalysisSnapshot snapshot = snapshot(symbol);
        switch (symbol) {
            case "NVDA", "TSLA", "AMD" -> {
                snapshot.setVolatilityLabel("high volatility");
                snapshot.setTrend("strong uptrend");
                snapshot.setPriceConsistency("smooth upward movement");
            }
            case "KO", "JNJ" -> {
                snapshot.setVolatilityLabel("low volatility");
                snapshot.setTrend("steady uptrend");
                snapshot.setPriceConsistency("smooth upward movement");
            }
            case "MSFT", "AAPL", "GOOG" -> {
                snapshot.setVolatilityLabel("medium volatility");
                snapshot.setTrend("steady uptrend");
                snapshot.setPriceConsistency("smooth upward movement");
            }
            default -> {
            }
        }
        snapshot.setSnapshotHash("adaptive-hash-" + symbol);
        return snapshot;
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
