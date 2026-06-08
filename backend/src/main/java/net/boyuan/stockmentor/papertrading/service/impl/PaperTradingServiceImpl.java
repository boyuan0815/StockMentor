package net.boyuan.stockmentor.papertrading.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.papertrading.dto.*;
import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.papertrading.model.PaperTradingAccountStatus;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.papertrading.service.PaperTradingService;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaperTradingServiceImpl implements PaperTradingService {
    private static final int MONEY_SCALE = 4;
    private static final int PERCENT_SCALE = 2;
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.stream(StockMetadata.SYMBOLS.split(","))
            .map(String::trim)
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .toList();

    private final CurrentUserService currentUserService;
    private final PaperTradingAccountRepository accountRepository;
    private final PaperPositionRepository positionRepository;
    private final PaperTradeTransactionRepository transactionRepository;
    private final StockRepository stockRepository;
    private final UserBehaviorProfileService behaviorProfileService;

    @Value("${stockmentor.paper-trading.initial-cash:1000000.00}")
    private BigDecimal initialCash;

    @Override
    @Transactional
    public PaperTradingAccountResponse getCurrentUserAccount() {
        AppUser user = currentUserService.getCurrentUser();
        return toAccountResponse(getOrCreateAccount(user));
    }

    @Override
    @Transactional
    public PaperPortfolioResponse getCurrentUserPortfolio() {
        AppUser user = currentUserService.getCurrentUser();
        PaperTradingAccount account = getOrCreateAccount(user);
        return toPortfolioResponse(account, positionRepository.findByUserUserId(user.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperTradeTransactionResponse> getCurrentUserTransactions() {
        AppUser user = currentUserService.getCurrentUser();
        return transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(user.getUserId()).stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    @Override
    @Transactional
    public PaperTradeExecutionResponse buyForCurrentUser(PaperTradeRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        String symbol = validateSymbol(request == null ? null : request.symbol());
        int quantity = validateQuantity(request == null ? null : request.quantity());
        PaperTradingAccount account = getOrCreateAccount(user);
        Stock stock = loadValidStock(symbol);
        BigDecimal executionPrice = money(stock.getCurrentPrice());
        BigDecimal grossAmount = money(executionPrice.multiply(BigDecimal.valueOf(quantity)));

        if (account.getCashBalance().compareTo(grossAmount) < 0) {
            throw new IllegalArgumentException("Insufficient virtual cash for paper trade");
        }

        LocalDateTime now = LocalDateTime.now();
        account.setCashBalance(money(account.getCashBalance().subtract(grossAmount)));
        account.setUpdatedAt(now);

        PaperPosition position = positionRepository.findByUserUserIdAndSymbol(user.getUserId(), symbol)
                .orElseGet(() -> newPosition(user, symbol, now));
        BigDecimal newTotalCost = money(position.getTotalCost().add(grossAmount));
        int newQuantity = position.getQuantity() + quantity;
        position.setQuantity(newQuantity);
        position.setTotalCost(newTotalCost);
        position.setAverageCost(money(newTotalCost.divide(BigDecimal.valueOf(newQuantity), MONEY_SCALE, RoundingMode.HALF_UP)));
        position.setUpdatedAt(now);

        PaperTradeTransaction transaction = transaction(user, symbol, PaperTradeSide.BUY, quantity, executionPrice, grossAmount, account.getCashBalance(), now);
        PaperPosition savedPosition = positionRepository.save(position);
        PaperTradeTransaction savedTransaction = transactionRepository.save(transaction);
        behaviorProfileService.recalculateBehaviorProfile(user.getUserId());

        return new PaperTradeExecutionResponse(
                toAccountResponse(account),
                toPositionResponse(savedPosition, stock),
                toTransactionResponse(savedTransaction)
        );
    }

    @Override
    @Transactional
    public PaperTradeExecutionResponse sellForCurrentUser(PaperTradeRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        String symbol = validateSymbol(request == null ? null : request.symbol());
        int quantity = validateQuantity(request == null ? null : request.quantity());
        PaperTradingAccount account = getOrCreateAccount(user);
        Stock stock = loadValidStock(symbol);
        BigDecimal executionPrice = money(stock.getCurrentPrice());

        PaperPosition position = positionRepository.findByUserUserIdAndSymbol(user.getUserId(), symbol)
                .orElseThrow(() -> new IllegalArgumentException("No paper position exists for symbol: " + symbol));
        if (position.getQuantity() < quantity) {
            throw new IllegalArgumentException("Sell quantity exceeds held paper shares");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal grossAmount = money(executionPrice.multiply(BigDecimal.valueOf(quantity)));
        account.setCashBalance(money(account.getCashBalance().add(grossAmount)));
        account.setUpdatedAt(now);

        int newQuantity = position.getQuantity() - quantity;
        PaperPosition responsePosition = null;
        if (newQuantity == 0) {
            positionRepository.delete(position);
        } else {
            BigDecimal sellCostBasis = money(position.getAverageCost().multiply(BigDecimal.valueOf(quantity)));
            BigDecimal newTotalCost = money(position.getTotalCost().subtract(sellCostBasis));
            position.setQuantity(newQuantity);
            position.setTotalCost(newTotalCost);
            position.setAverageCost(money(newTotalCost.divide(BigDecimal.valueOf(newQuantity), MONEY_SCALE, RoundingMode.HALF_UP)));
            position.setUpdatedAt(now);
            responsePosition = positionRepository.save(position);
        }

        PaperTradeTransaction transaction = transaction(user, symbol, PaperTradeSide.SELL, quantity, executionPrice, grossAmount, account.getCashBalance(), now);
        PaperTradeTransaction savedTransaction = transactionRepository.save(transaction);
        behaviorProfileService.recalculateBehaviorProfile(user.getUserId());

        return new PaperTradeExecutionResponse(
                toAccountResponse(account),
                responsePosition == null ? null : toPositionResponse(responsePosition, stock),
                toTransactionResponse(savedTransaction)
        );
    }

    private PaperTradingAccount getOrCreateAccount(AppUser user) {
        return accountRepository.findByUserUserId(user.getUserId())
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    PaperTradingAccount account = new PaperTradingAccount();
                    account.setUser(user);
                    account.setCashBalance(money(initialCash));
                    account.setStartingCash(money(initialCash));
                    account.setStatus(PaperTradingAccountStatus.ACTIVE);
                    account.setCreatedAt(now);
                    account.setUpdatedAt(now);
                    return accountRepository.save(account);
                });
    }

    private String validateSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SYMBOLS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported paper-trading symbol: " + symbol);
        }
        return normalized;
    }

    private int validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive whole number");
        }
        return quantity;
    }

    private Stock loadValidStock(String symbol) {
        Stock stock = stockRepository.findBySymbolIn(List.of(symbol)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stock row is missing for symbol: " + symbol));
        if (stock.getCurrentPrice() == null) {
            throw new IllegalArgumentException("Current price is missing for symbol: " + symbol);
        }
        if (stock.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Current price must be positive for symbol: " + symbol);
        }
        return stock;
    }

    private PaperPosition newPosition(AppUser user, String symbol, LocalDateTime now) {
        PaperPosition position = new PaperPosition();
        position.setUser(user);
        position.setSymbol(symbol);
        position.setQuantity(0);
        position.setAverageCost(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        position.setTotalCost(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        position.setRealizedPl(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        position.setCreatedAt(now);
        position.setUpdatedAt(now);
        return position;
    }

    private PaperTradeTransaction transaction(
            AppUser user,
            String symbol,
            PaperTradeSide side,
            int quantity,
            BigDecimal executionPrice,
            BigDecimal grossAmount,
            BigDecimal cashBalanceAfter,
            LocalDateTime executedAt
    ) {
        PaperTradeTransaction transaction = new PaperTradeTransaction();
        transaction.setUser(user);
        transaction.setSymbol(symbol);
        transaction.setSide(side);
        transaction.setQuantity(quantity);
        transaction.setExecutionPrice(executionPrice);
        transaction.setGrossAmount(grossAmount);
        transaction.setCashBalanceAfter(cashBalanceAfter);
        transaction.setExecutedAt(executedAt);
        return transaction;
    }

    private PaperPortfolioResponse toPortfolioResponse(PaperTradingAccount account, List<PaperPosition> positions) {
        Map<String, Stock> stockBySymbol = stockRepository.findBySymbolIn(
                        positions.stream().map(PaperPosition::getSymbol).toList()
                ).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));

        List<PaperPositionResponse> positionResponses = positions.stream()
                .map(position -> toPositionResponse(position, stockBySymbol.get(position.getSymbol())))
                .toList();

        BigDecimal totalInvestedCost = positionResponses.stream()
                .map(PaperPositionResponse::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedMarketValue = positionResponses.stream()
                .map(PaperPositionResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealizedProfitLoss = money(estimatedMarketValue.subtract(totalInvestedCost));
        BigDecimal estimatedPortfolioValue = money(account.getCashBalance().add(estimatedMarketValue));

        return new PaperPortfolioResponse(
                money(account.getCashBalance()),
                money(account.getStartingCash()),
                money(totalInvestedCost),
                money(estimatedMarketValue),
                estimatedPortfolioValue,
                unrealizedProfitLoss,
                positionResponses
        );
    }

    private PaperTradingAccountResponse toAccountResponse(PaperTradingAccount account) {
        return new PaperTradingAccountResponse(
                account.getAccountId(),
                money(account.getCashBalance()),
                money(account.getStartingCash()),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private PaperPositionResponse toPositionResponse(PaperPosition position, Stock stock) {
        BigDecimal currentPrice = stock == null || stock.getCurrentPrice() == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : money(stock.getCurrentPrice());
        BigDecimal marketValue = money(currentPrice.multiply(BigDecimal.valueOf(position.getQuantity())));
        BigDecimal unrealizedProfitLoss = money(marketValue.subtract(position.getTotalCost()));
        BigDecimal percent = position.getTotalCost().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                : unrealizedProfitLoss
                .multiply(BigDecimal.valueOf(100))
                .divide(position.getTotalCost(), PERCENT_SCALE, RoundingMode.HALF_UP);
        return new PaperPositionResponse(
                position.getPositionId(),
                position.getSymbol(),
                stock == null ? StockMetadata.COMPANY_MAP.getOrDefault(position.getSymbol(), position.getSymbol()) : stock.getCompanyName(),
                position.getQuantity(),
                money(position.getAverageCost()),
                money(position.getTotalCost()),
                currentPrice,
                marketValue,
                unrealizedProfitLoss,
                percent
        );
    }

    private PaperTradeTransactionResponse toTransactionResponse(PaperTradeTransaction transaction) {
        return new PaperTradeTransactionResponse(
                transaction.getTransactionId(),
                transaction.getSymbol(),
                transaction.getSide().name(),
                transaction.getQuantity(),
                money(transaction.getExecutionPrice()),
                money(transaction.getGrossAmount()),
                money(transaction.getCashBalanceAfter()),
                transaction.getExecutedAt()
        );
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
