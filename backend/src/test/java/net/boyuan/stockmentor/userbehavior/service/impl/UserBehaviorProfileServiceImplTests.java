package net.boyuan.stockmentor.userbehavior.service.impl;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.model.ConcentrationLevel;
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

    private UserBehaviorProfileServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new UserBehaviorProfileServiceImpl(
                behaviorProfileRepository,
                appUserRepository,
                transactionRepository,
                positionRepository,
                stockRepository
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
        when(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(behaviorProfileRepository.save(any(UserBehaviorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserBehaviorProfile profile = service.recalculateBehaviorProfile(1L);

        assertEquals(BehaviorConfidence.MEDIUM, profile.getBehaviorConfidence());
        assertEquals(TurnoverLevel.MEDIUM, profile.getTurnoverLevel());
        assertEquals(ConcentrationLevel.MODERATE, profile.getConcentrationLevel());
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
        assertEquals(40, profile.getBehaviorRiskScore());
        assertEquals(BehaviorConfidence.MEDIUM, profile.getBehaviorConfidence());
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
        PaperPosition position = new PaperPosition();
        position.setUser(user);
        position.setSymbol(symbol);
        position.setQuantity(quantity);
        position.setAverageCost(new BigDecimal("100.00"));
        position.setTotalCost(new BigDecimal("100.00").multiply(BigDecimal.valueOf(quantity)));
        position.setRealizedPl(BigDecimal.ZERO);
        position.setCreatedAt(LocalDateTime.now());
        position.setUpdatedAt(LocalDateTime.now());
        return position;
    }

    private Stock stock(String symbol, String price) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setCurrentPrice(new BigDecimal(price));
        return stock;
    }
}
