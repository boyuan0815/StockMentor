package net.boyuan.stockmentor.papertrading.service.impl;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.papertrading.dto.PaperPortfolioResponse;
import net.boyuan.stockmentor.papertrading.dto.PaperTradeExecutionResponse;
import net.boyuan.stockmentor.papertrading.dto.PaperTradeRequest;
import net.boyuan.stockmentor.papertrading.dto.PaperTradingAccountResponse;
import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.papertrading.model.PaperTradingAccountStatus;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTradingServiceImplTests {
    private static final BigDecimal INITIAL_CASH = new BigDecimal("1000000.00");
    private static final BigDecimal INITIAL_CASH_RESPONSE = new BigDecimal("1000000.0000");
    private static final BigDecimal TRADE_FEE = new BigDecimal("1.00");

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PaperTradingAccountRepository accountRepository;
    @Mock
    private PaperPositionRepository positionRepository;
    @Mock
    private PaperTradeTransactionRepository transactionRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private UserBehaviorProfileService behaviorProfileService;
    @Mock
    private DelayedMarketPriceService delayedMarketPriceService;

    private PaperTradingServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new PaperTradingServiceImpl(
                currentUserService,
                accountRepository,
                positionRepository,
                transactionRepository,
                stockRepository,
                behaviorProfileService,
                delayedMarketPriceService
        );
        ReflectionTestUtils.setField(service, "initialCash", INITIAL_CASH);
        ReflectionTestUtils.setField(service, "tradeFee", TRADE_FEE);
        user = user(1L);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(delayedMarketPriceService.resolveForDisplay(anyString()))
                .thenAnswer(invocation -> delayedPrice(invocation.getArgument(0), "100.00", true));
    }

    @Test
    void accountAutoCreatesWithDefaultInitialCash() {
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(accountRepository.save(any(PaperTradingAccount.class))).thenAnswer(invocation -> {
            PaperTradingAccount account = invocation.getArgument(0);
            account.setAccountId(11L);
            return account;
        });

        PaperTradingAccountResponse response = service.getCurrentUserAccount();

        assertEquals(11L, response.accountId());
        assertEquals(INITIAL_CASH_RESPONSE, response.cashBalance());
        assertEquals(INITIAL_CASH_RESPONSE, response.startingCash());
        assertEquals(1, response.currentSessionNumber());
        assertNull(response.lastResetAt());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void existingAccountIsReused() {
        PaperTradingAccount existing = account(user, INITIAL_CASH.toPlainString());
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(existing));

        PaperTradingAccountResponse response = service.getCurrentUserAccount();

        assertEquals(existing.getAccountId(), response.accountId());
        verify(accountRepository, never()).save(any(PaperTradingAccount.class));
    }

    @Test
    void buySupportedSymbolUpdatesCashPositionTransactionAndBehavior() {
        PaperTradingAccount account = account(user, INITIAL_CASH.toPlainString());
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "100.00", true));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(positionRepository.save(any(PaperPosition.class))).thenAnswer(invocation -> {
            PaperPosition position = invocation.getArgument(0);
            position.setPositionId(21L);
            return position;
        });
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> {
            PaperTradeTransaction transaction = invocation.getArgument(0);
            transaction.setTransactionId(31L);
            return transaction;
        });

        PaperTradeExecutionResponse response = service.buyForCurrentUser(new PaperTradeRequest("msft", 3));

        assertEquals(INITIAL_CASH_RESPONSE.subtract(new BigDecimal("301.0000")), response.account().cashBalance());
        assertEquals(3, response.position().quantity());
        assertEquals(new BigDecimal("100.3333"), response.position().averageCost());
        assertEquals(new BigDecimal("301.0000"), response.position().totalCost());
        assertEquals(PaperTradeSide.BUY.name(), response.transaction().side());
        assertEquals(new BigDecimal("100.0000"), response.transaction().executionPrice());
        assertEquals(new BigDecimal("300.0000"), response.transaction().grossAmount());
        assertEquals(new BigDecimal("1.0000"), response.transaction().fee());
        assertEquals(new BigDecimal("301.0000"), response.transaction().netAmount());
        assertEquals(BigDecimal.ZERO.setScale(4), response.transaction().realizedProfitLoss());
        assertTrue(response.transaction().isCurrentSession());
        assertEquals(1, response.transaction().sessionNumber());
        verify(behaviorProfileService).recalculateBehaviorProfile(1L);
    }

    @Test
    void buyAfterDelayedCloseUsesDailyCloseWhenAvailable() {
        PaperTradingAccount account = account(user, INITIAL_CASH.toPlainString());
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice(
                "MSFT",
                "105.00",
                true,
                DelayedPriceFreshnessStatus.MARKET_CLOSED,
                DelayedMarketPriceService.DAILY_PRICE_SOURCE
        ));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(positionRepository.save(any(PaperPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperTradeExecutionResponse response = service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1));

        assertEquals(new BigDecimal("105.0000"), response.transaction().executionPrice());
        assertEquals("MARKET_CLOSED", response.delayedPriceMetadata().priceFreshnessStatus());
        assertEquals(DelayedMarketPriceService.DAILY_PRICE_SOURCE, response.delayedPriceMetadata().priceSource());
    }

    @Test
    void buyAfterDelayedCloseUsesPendingIntradayWhenDailyCloseIsUnavailable() {
        PaperTradingAccount account = account(user, INITIAL_CASH.toPlainString());
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice(
                "MSFT",
                "104.50",
                true,
                DelayedPriceFreshnessStatus.MARKET_CLOSED_PENDING_DAILY_CLOSE,
                DelayedMarketPriceService.INTRADAY_PRICE_SOURCE
        ));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(positionRepository.save(any(PaperPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperTradeExecutionResponse response = service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1));

        assertEquals(new BigDecimal("104.5000"), response.transaction().executionPrice());
        assertEquals("MARKET_CLOSED_PENDING_DAILY_CLOSE", response.delayedPriceMetadata().priceFreshnessStatus());
        assertEquals(DelayedMarketPriceService.INTRADAY_PRICE_SOURCE, response.delayedPriceMetadata().priceSource());
        assertTrue(response.delayedPriceMetadata().dataNote().contains("daily close is not available yet"));
    }

    @Test
    void buyRejectsDisplayOnlyDailyFallbackWhenTradeIsNotExecutable() {
        PaperTradingAccount account = account(user, INITIAL_CASH.toPlainString());
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice(
                "MSFT",
                "102.00",
                false,
                DelayedPriceFreshnessStatus.FALLBACK_DAILY,
                DelayedMarketPriceService.DAILY_PRICE_SOURCE
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1))
        );

        verify(positionRepository, never()).save(any(PaperPosition.class));
        verify(transactionRepository, never()).save(any(PaperTradeTransaction.class));
        verify(behaviorProfileService, never()).recalculateBehaviorProfile(anyLong());
    }

    @Test
    void buyRejectsInvalidInputsWithoutPartialPersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("META", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", 0)));
        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", -1)));

        verify(stockRepository, never()).findBySymbolIn(anyCollection());
        verify(positionRepository, never()).save(any(PaperPosition.class));
        verify(transactionRepository, never()).save(any(PaperTradeTransaction.class));
        verify(behaviorProfileService, never()).recalculateBehaviorProfile(anyLong());
    }

    @Test
    void buyRejectsMissingStockUnavailableDelayedPriceAndInsufficientCash() {
        PaperTradingAccount account = account(user, "100.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection()))
                .thenReturn(List.of())
                .thenReturn(List.of(stock("MSFT", "100.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT"))
                .thenReturn(delayedPrice("MSFT", null, false))
                .thenReturn(delayedPrice("MSFT", "100.00", true));

        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", 2)));

        verify(positionRepository, never()).save(any(PaperPosition.class));
        verify(transactionRepository, never()).save(any(PaperTradeTransaction.class));
        verify(behaviorProfileService, never()).recalculateBehaviorProfile(anyLong());
    }

    @Test
    void sellExistingHoldingUpdatesCashPositionTransactionAndBehavior() {
        PaperTradingAccount account = account(user, "5000.00");
        PaperPosition position = position(user, "MSFT", 5, "100.00", "500.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "120.00", true));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(position));
        when(positionRepository.save(any(PaperPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> {
            PaperTradeTransaction transaction = invocation.getArgument(0);
            transaction.setTransactionId(41L);
            return transaction;
        });

        PaperTradeExecutionResponse response = service.sellForCurrentUser(new PaperTradeRequest("MSFT", 2));

        assertEquals(new BigDecimal("5239.0000"), response.account().cashBalance());
        assertEquals(3, response.position().quantity());
        assertEquals(new BigDecimal("300.0000"), response.position().totalCost());
        assertEquals(PaperTradeSide.SELL.name(), response.transaction().side());
        assertEquals(new BigDecimal("1.0000"), response.transaction().fee());
        assertEquals(new BigDecimal("239.0000"), response.transaction().netAmount());
        assertEquals(new BigDecimal("39.0000"), response.transaction().realizedProfitLoss());
        verify(positionRepository, never()).delete(any(PaperPosition.class));
        verify(behaviorProfileService).recalculateBehaviorProfile(1L);
    }

    @Test
    void fullSellRemovesPositionRow() {
        PaperTradingAccount account = account(user, "5000.00");
        PaperPosition position = position(user, "MSFT", 2, "100.00", "200.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "120.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "120.00", true));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(position));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperTradeExecutionResponse response = service.sellForCurrentUser(new PaperTradeRequest("MSFT", 2));

        assertNull(response.position());
        verify(positionRepository).delete(position);
        verify(positionRepository, never()).save(any(PaperPosition.class));
    }

    @Test
    void fullSellUsesPositionTotalCostForRealizedProfitLoss() {
        PaperTradingAccount account = account(user, "5000.00");
        PaperPosition position = position(user, "MSFT", 7, "412.1314", "2884.9200");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "411.76", true));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(position));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperTradeExecutionResponse response = service.sellForCurrentUser(new PaperTradeRequest("MSFT", 7));

        assertNull(response.position());
        assertEquals(new BigDecimal("2881.3200"), response.transaction().netAmount());
        assertEquals(new BigDecimal("-3.6000"), response.transaction().realizedProfitLoss());
        verify(positionRepository).delete(position);
    }

    @Test
    void sellRejectsMissingPositionAndExcessQuantityWithoutPartialPersistence() {
        PaperTradingAccount account = account(user, "5000.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "120.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "120.00", true));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(position(user, "MSFT", 1, "100.00", "100.00")));

        assertThrows(IllegalArgumentException.class, () -> service.sellForCurrentUser(new PaperTradeRequest("MSFT", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.sellForCurrentUser(new PaperTradeRequest("MSFT", 2)));

        verify(positionRepository, never()).save(any(PaperPosition.class));
        verify(positionRepository, never()).delete(any(PaperPosition.class));
        verify(transactionRepository, never()).save(any(PaperTradeTransaction.class));
        verify(behaviorProfileService, never()).recalculateBehaviorProfile(anyLong());
    }

    @Test
    void sellRejectsDisplayOnlyDailyFallbackWhenTradeIsNotExecutable() {
        PaperTradingAccount account = account(user, "5000.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice(
                "MSFT",
                "102.00",
                false,
                DelayedPriceFreshnessStatus.FALLBACK_DAILY,
                DelayedMarketPriceService.DAILY_PRICE_SOURCE
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.sellForCurrentUser(new PaperTradeRequest("MSFT", 1))
        );

        assertTrue(exception.getMessage().contains("Delayed market price"));

        verify(positionRepository, never()).save(any(PaperPosition.class));
        verify(positionRepository, never()).delete(any(PaperPosition.class));
        verify(transactionRepository, never()).save(any(PaperTradeTransaction.class));
        verify(behaviorProfileService, never()).recalculateBehaviorProfile(anyLong());
    }

    @Test
    void portfolioAndTransactionsAreScopedToCurrentUser() {
        PaperTradingAccount account = account(user, "9500.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByUserUserIdOrderBySymbolAsc(1L)).thenReturn(List.of(position(user, "MSFT", 5, "100.00", "500.00")));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "110.00", true));
        when(transactionRepository.sumCurrentSessionRealizedProfitLossByUserIdAndSide(1L, PaperTradeSide.SELL)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumCurrentSessionFeeByUserIdAndSideIn(eq(1L), anyList())).thenReturn(BigDecimal.ZERO);
        PaperTradeTransaction transaction = transaction(user, "MSFT", PaperTradeSide.BUY, 5, "100.00", "9500.00");
        when(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(1L)).thenReturn(List.of(transaction));

        PaperPortfolioResponse portfolio = service.getCurrentUserPortfolio();

        assertEquals(1, portfolio.positions().size());
        assertEquals(new BigDecimal("10050.0000"), portfolio.totalPortfolioValue());
        assertTrue(portfolio.portfolioValuationComplete());
        assertEquals(1, portfolio.pricedPositionCount());
        assertEquals(0, portfolio.unpricedPositionCount());
        assertEquals(BigDecimal.ZERO.setScale(4), portfolio.realizedProfitLoss());
        assertEquals(BigDecimal.ZERO.setScale(4), portfolio.totalFeesPaid());
        assertEquals(1, service.getCurrentUserTransactions(null, null, null, null, null, null, null).size());
        verify(positionRepository).findByUserUserIdOrderBySymbolAsc(1L);
        verify(transactionRepository).findTop50ByUserUserIdOrderByExecutedAtDesc(1L);
    }

    @Test
    void resetRestoresCashClearsCurrentUserPositionsCreatesResetMarkerAndSkipsBehavior() {
        PaperTradingAccount account = account(user, "5000.00");
        account.setCurrentSessionNumber(2);
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> {
            PaperTradeTransaction transaction = invocation.getArgument(0);
            transaction.setTransactionId(51L);
            return transaction;
        });
        when(transactionRepository.sumCurrentSessionRealizedProfitLossByUserIdAndSide(1L, PaperTradeSide.SELL)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumCurrentSessionFeeByUserIdAndSideIn(eq(1L), anyList())).thenReturn(BigDecimal.ZERO);

        PaperPortfolioResponse response = service.resetCurrentUserPortfolio();

        assertEquals(INITIAL_CASH_RESPONSE, response.cashBalance());
        assertEquals(INITIAL_CASH_RESPONSE, response.startingCash());
        assertEquals(3, response.currentSessionNumber());
        assertNotNull(response.lastResetAt());
        assertTrue(response.positions().isEmpty());
        verify(transactionRepository).markCurrentSessionFalseByUserId(1L);
        verify(positionRepository).deleteByUserUserId(1L);
        verify(transactionRepository).save(argThat(transaction ->
                transaction.getSide() == PaperTradeSide.RESET
                        && transaction.getSymbol() == null
                        && transaction.getQuantity() == 0
                        && Boolean.TRUE.equals(transaction.getIsCurrentSession())
                        && transaction.getSessionNumber() == 3
        ));
        verify(behaviorProfileService, never()).recalculateBehaviorProfile(anyLong());
    }

    @Test
    void buyBehaviorFailureDoesNotRollbackTradeData() {
        PaperTradingAccount account = account(user, INITIAL_CASH.toPlainString());
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "100.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "100.00", true));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(positionRepository.save(any(PaperPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("analytics failed")).when(behaviorProfileService).recalculateBehaviorProfile(1L);

        PaperTradeExecutionResponse response = service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1));

        assertEquals(new BigDecimal("999899.0000"), response.account().cashBalance());
        assertNull(response.position().portfolioWeightPercent());
        verify(positionRepository).save(any(PaperPosition.class));
        verify(transactionRepository).save(any(PaperTradeTransaction.class));
    }

    @Test
    void paperTradingServiceDoesNotInjectProviderOrAiClients() {
        Set<String> fieldTypeNames = Arrays.stream(PaperTradingServiceImpl.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.market.stock.service.StockApiClient"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.ai.service.OpenAiClient"));
        assertFalse(fieldTypeNames.contains("net.boyuan.stockmentor.scheduler.StockScheduler"));
    }

    @Test
    void portfolioMarksPartialValuationWhenDelayedPriceIsUnavailable() {
        PaperTradingAccount account = account(user, "9500.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByUserUserIdOrderBySymbolAsc(1L)).thenReturn(List.of(
                position(user, "MSFT", 5, "100.00", "500.00"),
                position(user, "KO", 2, "80.00", "160.00")
        ));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "999.00"), stock("KO", "999.00")));
        when(delayedMarketPriceService.resolveForDisplay("MSFT")).thenReturn(delayedPrice("MSFT", "110.00", true));
        when(delayedMarketPriceService.resolveForDisplay("KO")).thenReturn(delayedPrice("KO", null, false));
        when(transactionRepository.sumCurrentSessionRealizedProfitLossByUserIdAndSide(1L, PaperTradeSide.SELL)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumCurrentSessionFeeByUserIdAndSideIn(eq(1L), anyList())).thenReturn(BigDecimal.ZERO);

        PaperPortfolioResponse portfolio = service.getCurrentUserPortfolio();

        assertEquals(new BigDecimal("10050.0000"), portfolio.totalPortfolioValue());
        assertFalse(portfolio.portfolioValuationComplete());
        assertEquals(1, portfolio.pricedPositionCount());
        assertEquals(1, portfolio.unpricedPositionCount());
        assertNotNull(portfolio.portfolioDataNote());
        assertNull(portfolio.positions().stream()
                .filter(position -> "KO".equals(position.symbol()))
                .findFirst()
                .orElseThrow()
                .marketValue());
    }

    private AppUser user(Long userId) {
        AppUser appUser = new AppUser();
        appUser.setUserId(userId);
        appUser.setEmail("user" + userId + "@example.com");
        appUser.setUsername("user" + userId);
        appUser.setRole(AppUserRole.BEGINNER_INVESTOR);
        appUser.setStatus(AppUserStatus.ACTIVE);
        appUser.setIsDeleted(false);
        return appUser;
    }

    private PaperTradingAccount account(AppUser user, String cashBalance) {
        PaperTradingAccount account = new PaperTradingAccount();
        account.setAccountId(10L);
        account.setUser(user);
        account.setCashBalance(new BigDecimal(cashBalance));
        account.setStartingCash(INITIAL_CASH);
        account.setCurrentSessionNumber(1);
        account.setStatus(PaperTradingAccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now().minusDays(1));
        account.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return account;
    }

    private PaperPosition position(AppUser user, String symbol, int quantity, String averageCost, String totalCost) {
        PaperPosition position = new PaperPosition();
        position.setPositionId(20L);
        position.setUser(user);
        position.setSymbol(symbol);
        position.setQuantity(quantity);
        position.setAverageCost(new BigDecimal(averageCost));
        position.setTotalCost(new BigDecimal(totalCost));
        position.setRealizedPl(BigDecimal.ZERO);
        position.setCreatedAt(LocalDateTime.now().minusDays(1));
        position.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return position;
    }

    private Stock stock(String symbol, String price) {
        Stock stock = new Stock();
        stock.setStockId(5L);
        stock.setSymbol(symbol);
        stock.setCompanyName("Microsoft");
        stock.setCurrentPrice(price == null ? null : new BigDecimal(price));
        return stock;
    }

    private PaperTradeTransaction transaction(AppUser user, String symbol, PaperTradeSide side, int quantity, String price, String cashAfter) {
        PaperTradeTransaction transaction = new PaperTradeTransaction();
        transaction.setTransactionId(30L);
        transaction.setUser(user);
        transaction.setSymbol(symbol);
        transaction.setSide(side);
        transaction.setQuantity(quantity);
        transaction.setExecutionPrice(new BigDecimal(price));
        BigDecimal grossAmount = new BigDecimal(price).multiply(BigDecimal.valueOf(quantity));
        transaction.setGrossAmount(grossAmount);
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(grossAmount);
        transaction.setRealizedProfitLoss(BigDecimal.ZERO);
        transaction.setCashBalanceAfter(new BigDecimal(cashAfter));
        transaction.setIsCurrentSession(true);
        transaction.setSessionNumber(1);
        transaction.setExecutedAt(LocalDateTime.now());
        return transaction;
    }

    private DelayedMarketPrice delayedPrice(String symbol, String price, boolean executable) {
        return delayedPrice(
                symbol,
                price,
                executable,
                executable ? DelayedPriceFreshnessStatus.AVAILABLE : DelayedPriceFreshnessStatus.UNAVAILABLE,
                executable ? DelayedMarketPriceService.INTRADAY_PRICE_SOURCE : null
        );
    }

    private DelayedMarketPrice delayedPrice(
            String symbol,
            String price,
            boolean executable,
            DelayedPriceFreshnessStatus status,
            String source
    ) {
        return new DelayedMarketPrice(
                symbol,
                price == null ? null : new BigDecimal(price),
                new BigDecimal("0.5000"),
                price == null ? null : LocalDateTime.of(2026, 1, 5, 9, 45),
                LocalDateTime.of(2026, 1, 5, 9, 45),
                15,
                status,
                price != null,
                executable,
                executable
                        ? "Market is closed. This practice trade uses StockMentor's delayed stored price, not a live market quote. Today's daily close is not available yet when pending."
                        : "Delayed market price is not available yet. Please try again later.",
                source,
                "America/New_York",
                price == null ? null : LocalDateTime.of(2026, 1, 5, 10, 0),
                LocalDateTime.of(2026, 1, 5, 9, 45).toLocalDate()
        );
    }
}
