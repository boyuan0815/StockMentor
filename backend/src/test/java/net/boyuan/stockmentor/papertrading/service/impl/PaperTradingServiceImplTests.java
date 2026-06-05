package net.boyuan.stockmentor.papertrading.service.impl;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTradingServiceImplTests {
    private static final BigDecimal INITIAL_CASH = new BigDecimal("1000000.00");
    private static final BigDecimal INITIAL_CASH_RESPONSE = new BigDecimal("1000000.0000");

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
                behaviorProfileService
        );
        ReflectionTestUtils.setField(service, "initialCash", INITIAL_CASH);
        user = user(1L);
        when(currentUserService.getCurrentUser()).thenReturn(user);
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
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "100.00")));
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

        assertEquals(INITIAL_CASH_RESPONSE.subtract(new BigDecimal("300.0000")), response.account().cashBalance());
        assertEquals(3, response.position().quantity());
        assertEquals(new BigDecimal("100.0000"), response.position().averageCost());
        assertEquals(new BigDecimal("300.0000"), response.position().totalCost());
        assertEquals(PaperTradeSide.BUY.name(), response.transaction().side());
        assertEquals(new BigDecimal("300.0000"), response.transaction().grossAmount());
        verify(behaviorProfileService).recalculateBehaviorProfile(1L);
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
    void buyRejectsMissingStockMissingPriceNonPositivePriceAndInsufficientCash() {
        PaperTradingAccount account = account(user, "100.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection()))
                .thenReturn(List.of())
                .thenReturn(List.of(stock("MSFT", null)))
                .thenReturn(List.of(stock("MSFT", "0.00")))
                .thenReturn(List.of(stock("MSFT", "100.00")));

        assertThrows(IllegalArgumentException.class, () -> service.buyForCurrentUser(new PaperTradeRequest("MSFT", 1)));
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
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "120.00")));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(position));
        when(positionRepository.save(any(PaperPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> {
            PaperTradeTransaction transaction = invocation.getArgument(0);
            transaction.setTransactionId(41L);
            return transaction;
        });

        PaperTradeExecutionResponse response = service.sellForCurrentUser(new PaperTradeRequest("MSFT", 2));

        assertEquals(new BigDecimal("5240.0000"), response.account().cashBalance());
        assertEquals(3, response.position().quantity());
        assertEquals(new BigDecimal("300.0000"), response.position().totalCost());
        assertEquals(PaperTradeSide.SELL.name(), response.transaction().side());
        verify(positionRepository, never()).delete(any(PaperPosition.class));
        verify(behaviorProfileService).recalculateBehaviorProfile(1L);
    }

    @Test
    void fullSellRemovesPositionRow() {
        PaperTradingAccount account = account(user, "5000.00");
        PaperPosition position = position(user, "MSFT", 2, "100.00", "200.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "120.00")));
        when(positionRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(position));
        when(transactionRepository.save(any(PaperTradeTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperTradeExecutionResponse response = service.sellForCurrentUser(new PaperTradeRequest("MSFT", 2));

        assertNull(response.position());
        verify(positionRepository).delete(position);
        verify(positionRepository, never()).save(any(PaperPosition.class));
    }

    @Test
    void sellRejectsMissingPositionAndExcessQuantityWithoutPartialPersistence() {
        PaperTradingAccount account = account(user, "5000.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "120.00")));
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
    void portfolioAndTransactionsAreScopedToCurrentUser() {
        PaperTradingAccount account = account(user, "9500.00");
        when(accountRepository.findByUserUserId(1L)).thenReturn(Optional.of(account));
        when(positionRepository.findByUserUserId(1L)).thenReturn(List.of(position(user, "MSFT", 5, "100.00", "500.00")));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT", "110.00")));
        PaperTradeTransaction transaction = transaction(user, "MSFT", PaperTradeSide.BUY, 5, "100.00", "9500.00");
        when(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(1L)).thenReturn(List.of(transaction));

        PaperPortfolioResponse portfolio = service.getCurrentUserPortfolio();

        assertEquals(1, portfolio.positions().size());
        assertEquals(new BigDecimal("10050.0000"), portfolio.estimatedPortfolioValue());
        assertEquals(1, service.getCurrentUserTransactions().size());
        verify(positionRepository).findByUserUserId(1L);
        verify(transactionRepository).findTop50ByUserUserIdOrderByExecutedAtDesc(1L);
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
        transaction.setGrossAmount(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        transaction.setCashBalanceAfter(new BigDecimal(cashAfter));
        transaction.setExecutedAt(LocalDateTime.now());
        return transaction;
    }
}
