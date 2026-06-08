package net.boyuan.stockmentor.userbehavior.service.impl;

import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.papertrading.model.PaperTradingAccountStatus;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.model.ConcentrationLevel;
import net.boyuan.stockmentor.userbehavior.model.HighVolatilityExposure;
import net.boyuan.stockmentor.userbehavior.model.TurnoverLevel;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBehaviorProfileServiceImplTests {
    @Mock
    private UserBehaviorProfileRepository behaviorProfileRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PaperTradeTransactionRepository transactionRepository;
    @Mock
    private PaperPositionRepository positionRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockAnalysisSnapshotRepository snapshotRepository;
    @Mock
    private PaperTradingAccountRepository accountRepository;

    private UserBehaviorProfileServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new UserBehaviorProfileServiceImpl(
                behaviorProfileRepository,
                appUserRepository,
                transactionRepository,
                positionRepository,
                stockRepository,
                snapshotRepository,
                accountRepository
        );
        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("beginner@example.com");
        user.setUsername("beginner");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);
        lenient().when(appUserRepository.findByUserIdAndStatusAndIsDeletedFalse(1L, AppUserStatus.ACTIVE)).thenReturn(Optional.of(user));
        lenient().when(positionRepository.findByUserUserId(1L)).thenReturn(List.of());
        lenient().when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of());
        lenient().when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(anyCollection(), anyString())).thenReturn(List.of());
        lenient().when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account("0.00")));
    }

    @Test
    void createsLowConfidenceProfileWhenMissing() {
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> {
            UserBehaviorProfile profile = invocation.getArgument(0);
            profile.setBehaviorProfileId(10L);
            return profile;
        });

        UserBehaviorProfile profile = service.createLowConfidenceProfileIfMissing(user);

        assertEquals(BehaviorConfidence.LOW, profile.getBehaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, profile.getBehaviorStyle());
        assertNotNull(profile.getCreatedAt());
        verify(behaviorProfileRepository).save(any(UserBehaviorProfile.class));
    }

    @Test
    void reusesExistingBehaviorProfileInsteadOfCreatingDuplicate() {
        UserBehaviorProfile existing = new UserBehaviorProfile();
        existing.setBehaviorProfileId(10L);
        existing.setUser(user);
        existing.setBehaviorConfidence(BehaviorConfidence.LOW);
        existing.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(existing));

        UserBehaviorProfile profile = service.createLowConfidenceProfileIfMissing(user);

        assertSame(existing, profile);
        verify(behaviorProfileRepository, never()).save(any(UserBehaviorProfile.class));
    }

    @Test
    void summaryIsLowConfidenceWhenNoProfileExistsAndDoesNotCreateProfile() {
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        BehaviorSummaryForSuggestion summary = service.getBehaviorSummaryForSuggestion(1L);

        assertFalse(summary.hasProfile());
        assertEquals(BehaviorConfidence.LOW, summary.behaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, summary.behaviorStyle());
        verify(behaviorProfileRepository, never()).save(any(UserBehaviorProfile.class));
    }

    @Test
    void noTransactionsStaysLowInsufficientData() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any())).thenReturn(List.of());
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.LOW, profile.getBehaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, profile.getBehaviorStyle());
        assertNull(profile.getStockRiskExposureScore());
        assertEquals(TurnoverLevel.LOW, profile.getTurnoverLevel());
        assertNull(profile.getConcentrationScore());
        assertNull(profile.getAveragePositionSizePercent());
        assertNull(profile.getFavoriteRiskCategory());
        assertNull(profile.getMostTradedSymbols());
        assertTrue(profile.getBehaviorSummaryText().contains("limited"));
    }

    @Test
    void fewerThanThreeTransactionsStaysLow() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 1, "100.00")
                ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.LOW, profile.getBehaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, profile.getBehaviorStyle());
    }

    @Test
    void fewerThanTwoDistinctSymbolsStaysLow() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00")
                ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.LOW, profile.getBehaviorConfidence());
        assertEquals(UserBehaviorStyle.INSUFFICIENT_DATA, profile.getBehaviorStyle());
    }

    @Test
    void threeToNineTransactionsWithTwoSymbolsBecomesMedium() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "110.00")
                ));
        when(positionRepository.findByUserUserId(1L)).thenReturn(List.of(
                position("MSFT", 1),
                position("KO", 1)
        ));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(
                stock("MSFT", "100.00"),
                stock("KO", "100.00")
        ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account("800.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.MEDIUM, profile.getBehaviorConfidence());
        assertEquals(TurnoverLevel.MEDIUM, profile.getTurnoverLevel());
        assertEquals(ConcentrationLevel.MODERATE, profile.getConcentrationLevel());
    }

    @Test
    void enhancedBehaviorFieldsCalculateFromPaperTrades() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("KO", PaperTradeSide.SELL, 1, "110.00"),
                        transaction("MSFT", PaperTradeSide.BUY, 2, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 4, "100.00"),
                        transaction("NVDA", PaperTradeSide.BUY, 1, "100.00")
                ));
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(anyCollection(), eq("7D")))
                .thenReturn(List.of(
                        snapshot("KO", "conservative", "low"),
                        snapshot("MSFT", "moderate", "high"),
                        snapshot("NVDA", "aggressive", "very high")
                ));
        when(positionRepository.findByUserUserId(1L)).thenReturn(List.of(
                position("MSFT", 1),
                position("KO", 3)
        ));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(
                stock("MSFT", "100.00"),
                stock("KO", "100.00")
        ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.MEDIUM, profile.getBehaviorConfidence());
        assertEquals(UserBehaviorStyle.ACTIVE_TRADER, profile.getBehaviorStyle());
        assertEquals(42, profile.getStockRiskExposureScore());
        assertEquals(54, profile.getVolatilityExposureScore());
        assertEquals(62, profile.getBehaviorRiskScore());
        assertEquals(HighVolatilityExposure.MEDIUM, profile.getHighVolatilityExposure());
        assertEquals("conservative", profile.getFavoriteRiskCategory());
        assertEquals("KO,MSFT,NVDA", profile.getMostTradedSymbols());
        assertEquals(new BigDecimal("50.00"), profile.getAveragePositionSizePercent());
        assertEquals(75, profile.getConcentrationScore());
        assertEquals(ConcentrationLevel.CONCENTRATED, profile.getConcentrationLevel());
        assertEquals(100, profile.getTurnoverScore());
        assertEquals(TurnoverLevel.HIGH, profile.getTurnoverLevel());
        assertNull(profile.getHoldingPeriodScore());
        assertTrue(profile.getBehaviorSummaryText().contains("KO,MSFT,NVDA"));
        assertTrue(profile.getBehaviorSummaryText().contains("a conservative BUY-risk preference based on BUY activity"));
        assertTrue(profile.getBehaviorSummaryText().contains("concentrated holdings"));
        assertTrue(profile.getBehaviorSummaryText().contains("medium volatility exposure"));
        assertFalse(profile.getBehaviorSummaryText().contains("concentrated concentration"));
        assertFalse(profile.getBehaviorSummaryText().contains("a aggressive"));
    }

    @Test
    void turnoverScoreUsesTradeGrossDividedByCurrentAccountEquity() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 2, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "100.00")
                ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account("1000.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(40, profile.getTurnoverScore());
        assertEquals(TurnoverLevel.MEDIUM, profile.getTurnoverLevel());
    }

    @Test
    void turnoverScoreFallsBackToTransactionCountWhenAccountEquityUnavailable() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 2, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "100.00")
                ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(30, profile.getTurnoverScore());
        assertEquals(TurnoverLevel.LOW, profile.getTurnoverLevel());
    }

    @Test
    void turnoverScoreFallsBackToTransactionCountWhenAccountEquityIsNotPositive() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 2, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "100.00"),
                        transaction("KO", PaperTradeSide.SELL, 1, "100.00")
                ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account("0.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(40, profile.getTurnoverScore());
        assertEquals(TurnoverLevel.MEDIUM, profile.getTurnoverLevel());
    }

    @Test
    void sameTransactionCountCanProduceDifferentTurnoverScoresBasedOnGrossAmount() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "10.00"),
                        transaction("KO", PaperTradeSide.BUY, 1, "10.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "10.00")
                ))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "200.00"),
                        transaction("KO", PaperTradeSide.BUY, 1, "200.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "200.00")
                ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account("1000.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile smallTrades = service.recalculateBehaviorProfile(1L);
        UserBehaviorProfile largeTrades = service.recalculateBehaviorProfile(1L);

        assertEquals(3, smallTrades.getTurnoverScore());
        assertEquals(60, largeTrades.getTurnoverScore());
        assertTrue(largeTrades.getTurnoverScore() > smallTrades.getTurnoverScore());
    }

    @Test
    void snapshotRiskOverridesMetadataAndNormalizesLabels() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("KO", PaperTradeSide.BUY, 3, "100.00"),
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("AAPL", PaperTradeSide.BUY, 1, "100.00")
                ));
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(anyCollection(), eq("7D")))
                .thenReturn(List.of(
                        snapshot("KO", "AGGRESSIVE", "low"),
                        snapshot("MSFT", "moderate-aggressive", "medium"),
                        snapshot("AAPL", "Moderate_Aggressive", "moderate")
                ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(79, profile.getStockRiskExposureScore());
        assertEquals("aggressive", profile.getFavoriteRiskCategory());
        assertEquals(43, profile.getVolatilityExposureScore());
        assertTrue(profile.getBehaviorSummaryText().contains("an aggressive BUY-risk preference"));
        assertFalse(profile.getBehaviorSummaryText().contains("a aggressive"));
    }

    @Test
    void metadataRiskIsFallbackWhenSnapshotRiskIsMissing() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("KO", PaperTradeSide.BUY, 3, "100.00"),
                        transaction("NVDA", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "100.00")
                ));
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(anyCollection(), eq("7D")))
                .thenReturn(List.of(snapshot("KO", null, null)));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(40, profile.getStockRiskExposureScore());
        assertEquals("conservative", profile.getFavoriteRiskCategory());
        assertNull(profile.getVolatilityExposureScore());
        assertEquals(37, profile.getBehaviorRiskScore());
    }

    @Test
    void unknownSnapshotRiskFallsBackToMetadataAndMissingMetadataSkipsSymbol() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("KO", PaperTradeSide.BUY, 2, "100.00"),
                        transaction("ZZZZ", PaperTradeSide.BUY, 4, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "100.00")
                ));
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(anyCollection(), eq("7D")))
                .thenReturn(List.of(
                        snapshot("KO", "not-a-risk", "low"),
                        snapshot("ZZZZ", null, "high")
                ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(25, profile.getStockRiskExposureScore());
        assertEquals("conservative", profile.getFavoriteRiskCategory());
        assertEquals(62, profile.getVolatilityExposureScore());
    }

    @Test
    void nullAccountCashBalanceMakesEquityMetricsNullAndTurnoverUsesFallback() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("MSFT", PaperTradeSide.SELL, 1, "100.00")
                ));
        when(positionRepository.findByUserUserId(1L)).thenReturn(List.of(
                position("MSFT", 1),
                position("KO", 1)
        ));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(
                stock("MSFT", "100.00"),
                stock("KO", "100.00")
        ));
        PaperTradingAccount account = account(null);
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(50, profile.getConcentrationScore());
        assertEquals(ConcentrationLevel.MODERATE, profile.getConcentrationLevel());
        assertNull(profile.getAveragePositionSizePercent());
        assertEquals(30, profile.getTurnoverScore());
        assertEquals(TurnoverLevel.LOW, profile.getTurnoverLevel());
    }

    @Test
    void multipleSnapshotsForOneSymbolUseFirstRepositoryResultAsLatest() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("KO", PaperTradeSide.BUY, 2, "100.00"),
                        transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.SELL, 1, "100.00")
                ));
        StockAnalysisSnapshot latestKo = snapshot("KO", "aggressive", "high", 200L, LocalDateTime.now());
        StockAnalysisSnapshot olderKo = snapshot("KO", "conservative", "low", 100L, LocalDateTime.now().minusDays(1));
        when(snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(anyCollection(), eq("7D")))
                .thenReturn(List.of(
                        latestKo,
                        olderKo,
                        snapshot("MSFT", "moderate", "medium", 300L, LocalDateTime.now())
                ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(75, profile.getStockRiskExposureScore());
        assertEquals("aggressive", profile.getFavoriteRiskCategory());
        assertEquals(68, profile.getVolatilityExposureScore());
    }

    @Test
    void positionMetricsUseCurrentPriceFallbackTotalCostAndAccountEquity() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(positionRepository.findByUserUserId(1L)).thenReturn(List.of(
                position("MSFT", 30, "100.00", "3000.00"),
                position("KO", 10, "100.00", "1000.00"),
                position("NVDA", 10, "100.00", "1000.00")
        ));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(
                stock("MSFT", "100.00"),
                stock("KO", "100.00"),
                stock("NVDA", null)
        ));
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account("5000.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(60, profile.getConcentrationScore());
        assertEquals(ConcentrationLevel.MODERATE, profile.getConcentrationLevel());
        assertEquals(new BigDecimal("16.67"), profile.getAveragePositionSizePercent());
        assertEquals(26, profile.getBehaviorRiskScore());
    }

    @Test
    void positionMetricsAreNullWhenNoValidPositionValueOrEquityExists() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(positionRepository.findByUserUserId(1L))
                .thenReturn(List.of(position("MSFT", 0, "100.00", "0.00")))
                .thenReturn(List.of(position("MSFT", 1, "100.00", "100.00")));
        when(stockRepository.findBySymbolIn(anyCollection()))
                .thenReturn(List.of())
                .thenReturn(List.of(stock("MSFT", "100.00")));
        when(accountRepository.findByUserUserId(1L))
                .thenReturn(Optional.of(account("0.00")))
                .thenReturn(Optional.of(account("-100.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile noValidPositionValue = service.recalculateBehaviorProfile(1L);
        UserBehaviorProfile noEquity = service.recalculateBehaviorProfile(1L);

        assertNull(noValidPositionValue.getConcentrationScore());
        assertNull(noValidPositionValue.getAveragePositionSizePercent());
        assertEquals(100, noEquity.getConcentrationScore());
        assertNull(noEquity.getAveragePositionSizePercent());
    }

    @Test
    void tenTransactionsWithThreeSymbolsBecomesHigh() {
        List<PaperTradeTransaction> transactions = List.of(
                transaction("NVDA", PaperTradeSide.BUY, 1, "100.00"),
                transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                transaction("KO", PaperTradeSide.BUY, 1, "100.00"),
                transaction("NVDA", PaperTradeSide.BUY, 1, "100.00"),
                transaction("MSFT", PaperTradeSide.BUY, 1, "100.00"),
                transaction("KO", PaperTradeSide.BUY, 1, "100.00"),
                transaction("NVDA", PaperTradeSide.SELL, 1, "100.00"),
                transaction("MSFT", PaperTradeSide.SELL, 1, "100.00"),
                transaction("KO", PaperTradeSide.SELL, 1, "100.00"),
                transaction("NVDA", PaperTradeSide.BUY, 1, "100.00")
        );
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any())).thenReturn(transactions);
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.HIGH, profile.getBehaviorConfidence());
        assertEquals(TurnoverLevel.HIGH, profile.getTurnoverLevel());
    }

    @Test
    void riskExposureUsesBuyGrossAmountAndIgnoresSellGrossAmount() {
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        transaction("NVDA", PaperTradeSide.BUY, 1, "100.00"),
                        transaction("KO", PaperTradeSide.BUY, 3, "100.00"),
                        transaction("TSLA", PaperTradeSide.SELL, 100, "100.00")
                ));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(40, profile.getStockRiskExposureScore());
        assertEquals(37, profile.getBehaviorRiskScore());
        assertNull(profile.getVolatilityExposureScore());
        assertEquals(BehaviorConfidence.MEDIUM, profile.getBehaviorConfidence());
        assertEquals("conservative", profile.getFavoriteRiskCategory());
        assertEquals("TSLA,KO,NVDA", profile.getMostTradedSymbols());
    }

    @Test
    void summaryIncludesEnhancedBehaviorFields() {
        UserBehaviorProfile existing = new UserBehaviorProfile();
        existing.setBehaviorProfileId(99L);
        existing.setUser(user);
        existing.setBehaviorConfidence(BehaviorConfidence.MEDIUM);
        existing.setBehaviorStyle(UserBehaviorStyle.BALANCED);
        existing.setBehaviorRiskScore(42);
        existing.setAveragePositionSizePercent(new BigDecimal("50.00"));
        existing.setTurnoverLevel(TurnoverLevel.MEDIUM);
        existing.setConcentrationLevel(ConcentrationLevel.CONCENTRATED);
        existing.setHighVolatilityExposure(HighVolatilityExposure.LOW);
        existing.setStockRiskExposureScore(42);
        existing.setConcentrationScore(75);
        existing.setTurnoverScore(40);
        existing.setVolatilityExposureScore(42);
        existing.setFavoriteRiskCategory("conservative");
        existing.setMostTradedSymbols("KO,MSFT,NVDA");
        existing.setBehaviorSummaryText("Recent paper trades show medium confidence behavior.");
        existing.setUpdatedAt(LocalDateTime.now());
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(existing));

        BehaviorSummaryForSuggestion summary = service.getBehaviorSummaryForSuggestion(1L);

        assertEquals("conservative", summary.favoriteRiskCategory());
        assertEquals("KO,MSFT,NVDA", summary.mostTradedSymbols());
        assertEquals("Recent paper trades show medium confidence behavior.", summary.behaviorSummaryText());
        assertEquals("Paper-trading behavior is calculated from recent simulated trades only.", summary.sourceNote());
    }

    @Test
    void existingBehaviorProfileIsUpdatedInsteadOfDuplicatedAfterTradeRecalculation() {
        UserBehaviorProfile existing = new UserBehaviorProfile();
        existing.setBehaviorProfileId(99L);
        existing.setUser(user);
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));
        existing.setBehaviorConfidence(BehaviorConfidence.LOW);
        existing.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        when(transactionRepository.findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(any(), any(), any()))
                .thenReturn(List.of(transaction("MSFT", PaperTradeSide.BUY, 1, "100.00")));
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(existing));
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertSame(existing, profile);
        assertEquals(99L, profile.getBehaviorProfileId());
        verify(behaviorProfileRepository).save(existing);
    }

    private PaperTradeTransaction transaction(String symbol, PaperTradeSide side, int quantity, String price) {
        PaperTradeTransaction transaction = new PaperTradeTransaction();
        transaction.setUser(user);
        transaction.setSymbol(symbol);
        transaction.setSide(side);
        transaction.setQuantity(quantity);
        transaction.setExecutionPrice(new BigDecimal(price));
        transaction.setGrossAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        transaction.setCashBalanceAfter(BigDecimal.ZERO);
        transaction.setExecutedAt(LocalDateTime.now());
        return transaction;
    }

    private PaperPosition position(String symbol, int quantity) {
        return position(symbol, quantity, "100.00", new BigDecimal("100.00").multiply(BigDecimal.valueOf(quantity)).toPlainString());
    }

    private PaperPosition position(String symbol, int quantity, String averageCost, String totalCost) {
        PaperPosition position = new PaperPosition();
        position.setUser(user);
        position.setSymbol(symbol);
        position.setQuantity(quantity);
        position.setAverageCost(new BigDecimal(averageCost));
        position.setTotalCost(new BigDecimal(totalCost));
        position.setRealizedPl(BigDecimal.ZERO);
        position.setCreatedAt(LocalDateTime.now());
        position.setUpdatedAt(LocalDateTime.now());
        return position;
    }

    private Stock stock(String symbol, String price) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setCurrentPrice(price == null ? null : new BigDecimal(price));
        return stock;
    }

    private StockAnalysisSnapshot snapshot(String symbol, String riskCategory, String volatilityLabel) {
        return snapshot(symbol, riskCategory, volatilityLabel, (long) Math.abs(symbol.hashCode()), LocalDateTime.now());
    }

    private StockAnalysisSnapshot snapshot(
            String symbol,
            String riskCategory,
            String volatilityLabel,
            Long analysisSnapshotId,
            LocalDateTime createdAt
    ) {
        StockAnalysisSnapshot snapshot = new StockAnalysisSnapshot();
        snapshot.setAnalysisSnapshotId(analysisSnapshotId);
        snapshot.setSymbol(symbol);
        snapshot.setTimeframe("7D");
        snapshot.setRiskCategory(riskCategory);
        snapshot.setVolatilityLabel(volatilityLabel);
        snapshot.setSnapshotHash(symbol + "-hash");
        snapshot.setCreatedAt(createdAt);
        return snapshot;
    }

    private PaperTradingAccount account(String cashBalance) {
        PaperTradingAccount account = new PaperTradingAccount();
        account.setAccountId(10L);
        account.setUser(user);
        account.setCashBalance(cashBalance == null ? null : new BigDecimal(cashBalance));
        account.setStartingCash(new BigDecimal("1000000.00"));
        account.setStatus(PaperTradingAccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return account;
    }
}
