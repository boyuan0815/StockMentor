package net.boyuan.stockmentor.papertrading.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.exception.ResourceNotFoundException;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.dto.DelayedPriceMetadataResponse;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaperTradingServiceImpl implements PaperTradingService {
    private static final Logger log = LoggerFactory.getLogger(PaperTradingServiceImpl.class);
    private static final int MONEY_SCALE = 4;
    private static final int PERCENT_SCALE = 2;
    private static final int DEFAULT_TRANSACTION_SIZE = 50;
    private static final int MAX_TRANSACTION_SIZE = 100;
    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final String TODAY_PL_NOTE = "Percentage is based on current session starting cash.";
    private static final List<PaperTradeSide> FEE_SIDES = List.of(PaperTradeSide.BUY, PaperTradeSide.SELL);
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
    private final DelayedMarketPriceService delayedMarketPriceService;

    @Value("${stockmentor.paper-trading.initial-cash:1000000.00}")
    private BigDecimal initialCash;

    @Value("${stockmentor.paper-trading.trade-fee:1.00}")
    private BigDecimal tradeFee;

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
        return toPortfolioResponse(account, positionRepository.findByUserUserIdOrderBySymbolAsc(user.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperTradeTransactionResponse> getCurrentUserTransactions(
            String symbol,
            String side,
            String from,
            String to,
            Integer page,
            Integer size,
            Boolean currentSessionOnly
    ) {
        AppUser user = currentUserService.getCurrentUser();
        boolean hasFilters = hasText(symbol)
                || hasText(side)
                || hasText(from)
                || hasText(to)
                || page != null
                || size != null
                || Boolean.TRUE.equals(currentSessionOnly);

        if (!hasFilters) {
            return transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(user.getUserId()).stream()
                    .map(this::toTransactionResponse)
                    .toList();
        }

        TransactionQuery query = transactionQuery(user, symbol, side, from, to, page, size, currentSessionOnly);

        return transactionRepository.findAll(
                        transactionSpec(query.userId(), query.symbol(), query.side(), query.from(), query.to(), query.currentSessionOnly()),
                        transactionPageRequest(query.page(), query.size())
                ).stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaperTradeTransactionPageResponse getCurrentUserTransactionsPage(
            String symbol,
            String side,
            String from,
            String to,
            Integer page,
            Integer size,
            Boolean currentSessionOnly
    ) {
        AppUser user = currentUserService.getCurrentUser();
        TransactionQuery query = transactionQuery(user, symbol, side, from, to, page, size, currentSessionOnly);
        Page<PaperTradeTransaction> result = transactionRepository.findAll(
                transactionSpec(query.userId(), query.symbol(), query.side(), query.from(), query.to(), query.currentSessionOnly()),
                transactionPageRequest(query.page(), query.size())
        );
        return new PaperTradeTransactionPageResponse(
                result.getContent().stream().map(this::toTransactionResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaperTradeTransactionResponse getCurrentUserTransaction(Long transactionId) {
        if (transactionId == null) {
            throw new ResourceNotFoundException("Paper trade transaction not found");
        }
        AppUser user = currentUserService.getCurrentUser();
        return transactionRepository.findByTransactionIdAndUserUserId(transactionId, user.getUserId())
                .map(this::toTransactionResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Paper trade transaction not found"));
    }

    @Override
    @Transactional
    public PaperPortfolioResponse resetCurrentUserPortfolio() {
        AppUser user = currentUserService.getCurrentUser();
        PaperTradingAccount account = getOrCreateAccount(user);
        LocalDateTime now = LocalDateTime.now();
        int newSessionNumber = currentSessionNumber(account) + 1;

        transactionRepository.markCurrentSessionFalseByUserId(user.getUserId());
        positionRepository.deleteByUserUserId(user.getUserId());

        account.setCurrentSessionNumber(newSessionNumber);
        account.setCashBalance(money(initialCash));
        account.setStartingCash(money(initialCash));
        account.setLastResetAt(now);
        account.setUpdatedAt(now);

        PaperTradeTransaction resetTransaction = transaction(
                user,
                null,
                PaperTradeSide.RESET,
                0,
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                money(BigDecimal.ZERO),
                account.getCashBalance(),
                true,
                newSessionNumber,
                now
        );
        transactionRepository.save(resetTransaction);

        return toPortfolioResponse(account, List.of());
    }

    @Override
    @Transactional
    public PaperTradeExecutionResponse buyForCurrentUser(PaperTradeRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        String symbol = validateSymbol(request == null ? null : request.symbol());
        int quantity = validateQuantity(request == null ? null : request.quantity());
        PaperTradingAccount account = getOrCreateAccount(user);
        Stock stock = loadStockRow(symbol);
        DelayedMarketPrice executionPriceMetadata = requireExecutableDelayedPrice(symbol);
        BigDecimal executionPrice = money(executionPriceMetadata.displayedPrice());
        BigDecimal grossAmount = money(executionPrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal fee = currentTradeFee();
        BigDecimal netAmount = money(grossAmount.add(fee));

        if (account.getCashBalance().compareTo(netAmount) < 0) {
            throw new IllegalArgumentException("Insufficient virtual cash for paper trade");
        }

        LocalDateTime now = LocalDateTime.now();
        int sessionNumber = currentSessionNumber(account);
        account.setCashBalance(money(account.getCashBalance().subtract(netAmount)));
        account.setUpdatedAt(now);

        PaperPosition position = positionRepository.findByUserUserIdAndSymbol(user.getUserId(), symbol)
                .orElseGet(() -> newPosition(user, symbol, now));
        BigDecimal newTotalCost = money(position.getTotalCost().add(netAmount));
        int newQuantity = position.getQuantity() + quantity;
        position.setQuantity(newQuantity);
        position.setTotalCost(newTotalCost);
        position.setAverageCost(money(newTotalCost.divide(BigDecimal.valueOf(newQuantity), MONEY_SCALE, RoundingMode.HALF_UP)));
        position.setUpdatedAt(now);

        PaperTradeTransaction transaction = transaction(
                user,
                symbol,
                PaperTradeSide.BUY,
                quantity,
                executionPrice,
                grossAmount,
                fee,
                netAmount,
                money(BigDecimal.ZERO),
                account.getCashBalance(),
                true,
                sessionNumber,
                now
        );
        PaperPosition savedPosition = positionRepository.save(position);
        PaperTradeTransaction savedTransaction = transactionRepository.save(transaction);
        recalculateBehaviorAfterCommit(user.getUserId());

        return new PaperTradeExecutionResponse(
                toAccountResponse(account),
                toPositionResponse(savedPosition, stock, null, executionPriceMetadata),
                toTransactionResponse(savedTransaction),
                DelayedPriceMetadataResponse.from(executionPriceMetadata)
        );
    }

    @Override
    @Transactional
    public PaperTradeExecutionResponse sellForCurrentUser(PaperTradeRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        String symbol = validateSymbol(request == null ? null : request.symbol());
        int quantity = validateQuantity(request == null ? null : request.quantity());
        PaperTradingAccount account = getOrCreateAccount(user);
        Stock stock = loadStockRow(symbol);
        DelayedMarketPrice executionPriceMetadata = requireExecutableDelayedPrice(symbol);
        BigDecimal executionPrice = money(executionPriceMetadata.displayedPrice());

        PaperPosition position = positionRepository.findByUserUserIdAndSymbol(user.getUserId(), symbol)
                .orElseThrow(() -> new IllegalArgumentException("No paper position exists for symbol: " + symbol));
        if (position.getQuantity() < quantity) {
            throw new IllegalArgumentException("Sell quantity exceeds held paper shares");
        }

        LocalDateTime now = LocalDateTime.now();
        int sessionNumber = currentSessionNumber(account);
        BigDecimal grossAmount = money(executionPrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal fee = currentTradeFee();
        BigDecimal netAmount = money(grossAmount.subtract(fee));
        int newQuantity = position.getQuantity() - quantity;
        BigDecimal costBasisSold = newQuantity == 0
                ? money(position.getTotalCost())
                : money(position.getAverageCost().multiply(BigDecimal.valueOf(quantity)));
        BigDecimal realizedProfitLoss = money(netAmount.subtract(costBasisSold));
        account.setCashBalance(money(account.getCashBalance().add(netAmount)));
        account.setUpdatedAt(now);

        PaperPosition responsePosition = null;
        if (newQuantity == 0) {
            positionRepository.delete(position);
        } else {
            BigDecimal newTotalCost = money(position.getTotalCost().subtract(costBasisSold));
            position.setQuantity(newQuantity);
            position.setTotalCost(newTotalCost);
            position.setAverageCost(money(newTotalCost.divide(BigDecimal.valueOf(newQuantity), MONEY_SCALE, RoundingMode.HALF_UP)));
            position.setUpdatedAt(now);
            responsePosition = positionRepository.save(position);
        }

        PaperTradeTransaction transaction = transaction(
                user,
                symbol,
                PaperTradeSide.SELL,
                quantity,
                executionPrice,
                grossAmount,
                fee,
                netAmount,
                realizedProfitLoss,
                account.getCashBalance(),
                true,
                sessionNumber,
                now
        );
        PaperTradeTransaction savedTransaction = transactionRepository.save(transaction);
        recalculateBehaviorAfterCommit(user.getUserId());

        return new PaperTradeExecutionResponse(
                toAccountResponse(account),
                responsePosition == null ? null : toPositionResponse(responsePosition, stock, null, executionPriceMetadata),
                toTransactionResponse(savedTransaction),
                DelayedPriceMetadataResponse.from(executionPriceMetadata)
        );
    }

    private PaperTradingAccount getOrCreateAccount(AppUser user) {
        return accountRepository.findByUserUserId(user.getUserId())
                .map(account -> {
                    normalizeAccount(account);
                    return account;
                })
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    PaperTradingAccount account = new PaperTradingAccount();
                    account.setUser(user);
                    account.setCashBalance(money(initialCash));
                    account.setStartingCash(money(initialCash));
                    account.setCurrentSessionNumber(1);
                    account.setStatus(PaperTradingAccountStatus.ACTIVE);
                    account.setCreatedAt(now);
                    account.setUpdatedAt(now);
                    return accountRepository.save(account);
                });
    }

    private void normalizeAccount(PaperTradingAccount account) {
        if (account.getCurrentSessionNumber() == null || account.getCurrentSessionNumber() < 1) {
            account.setCurrentSessionNumber(1);
        }
        account.setCashBalance(money(account.getCashBalance()));
        account.setStartingCash(money(account.getStartingCash()));
    }

    private String validateSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SYMBOLS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported paper-trading symbol: " + symbol);
        }
        return normalized;
    }

    private PaperTradeSide parseSide(String side) {
        if ("ALL".equalsIgnoreCase(side.trim())) {
            return null;
        }
        try {
            return PaperTradeSide.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid paper trade side: " + side);
        }
    }

    private int validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive whole number");
        }
        return quantity;
    }

    private Stock loadStockRow(String symbol) {
        return stockRepository.findBySymbolIn(List.of(symbol)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stock row is missing for symbol: " + symbol));
    }

    private DelayedMarketPrice requireExecutableDelayedPrice(String symbol) {
        DelayedMarketPrice delayedPrice = delayedMarketPriceService.resolveForDisplay(symbol);
        if (!hasExecutableDelayedPrice(delayedPrice)) {
            throw new IllegalArgumentException("Delayed market price is not available yet. Please try again later.");
        }
        return delayedPrice;
    }

    private PaperPosition newPosition(AppUser user, String symbol, LocalDateTime now) {
        PaperPosition position = new PaperPosition();
        position.setUser(user);
        position.setSymbol(symbol);
        position.setQuantity(0);
        position.setAverageCost(money(BigDecimal.ZERO));
        position.setTotalCost(money(BigDecimal.ZERO));
        position.setRealizedPl(money(BigDecimal.ZERO));
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
            BigDecimal fee,
            BigDecimal netAmount,
            BigDecimal realizedProfitLoss,
            BigDecimal cashBalanceAfter,
            Boolean isCurrentSession,
            Integer sessionNumber,
            LocalDateTime executedAt
    ) {
        PaperTradeTransaction transaction = new PaperTradeTransaction();
        transaction.setUser(user);
        transaction.setSymbol(symbol);
        transaction.setSide(side);
        transaction.setQuantity(quantity);
        transaction.setExecutionPrice(executionPrice);
        transaction.setGrossAmount(grossAmount);
        transaction.setFee(fee);
        transaction.setNetAmount(netAmount);
        transaction.setRealizedProfitLoss(realizedProfitLoss);
        transaction.setCashBalanceAfter(cashBalanceAfter);
        transaction.setIsCurrentSession(isCurrentSession);
        transaction.setSessionNumber(sessionNumber);
        transaction.setExecutedAt(executedAt);
        return transaction;
    }

    private Specification<PaperTradeTransaction> transactionSpec(
            Long userId,
            String symbol,
            PaperTradeSide side,
            LocalDateTime from,
            LocalDateTime to,
            boolean currentSessionOnly
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("userId"), userId));
            if (symbol != null) {
                predicates.add(cb.equal(root.get("symbol"), symbol));
            }
            if (side != null) {
                predicates.add(cb.equal(root.get("side"), side));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("executedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("executedAt"), to));
            }
            if (currentSessionOnly) {
                predicates.add(cb.or(
                        cb.equal(root.get("isCurrentSession"), true),
                        cb.isNull(root.get("isCurrentSession"))
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private TransactionQuery transactionQuery(
            AppUser user,
            String symbol,
            String side,
            String from,
            String to,
            Integer page,
            Integer size,
            Boolean currentSessionOnly
    ) {
        String normalizedSymbol = hasText(symbol) ? validateSymbol(symbol) : null;
        PaperTradeSide parsedSide = hasText(side) ? parseSide(side) : null;
        LocalDateTime fromAt = parseDateTimeFilter(from, true);
        LocalDateTime toAt = parseDateTimeFilter(to, false);
        if (fromAt != null && toAt != null && fromAt.isAfter(toAt)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        return new TransactionQuery(
                user.getUserId(),
                normalizedSymbol,
                parsedSide,
                fromAt,
                toAt,
                normalizePage(page),
                normalizeSize(size),
                Boolean.TRUE.equals(currentSessionOnly)
        );
    }

    private PageRequest transactionPageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("executedAt"),
                Sort.Order.desc("transactionId")
        ));
    }

    private PaperPortfolioResponse toPortfolioResponse(PaperTradingAccount account, List<PaperPosition> positions) {
        Map<String, Stock> stockBySymbol = positions.isEmpty()
                ? Map.of()
                : stockRepository.findBySymbolIn(positions.stream().map(PaperPosition::getSymbol).toList()).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
        Map<String, DelayedMarketPrice> delayedPriceBySymbol = positions.isEmpty()
                ? Map.of()
                : positions.stream()
                .map(PaperPosition::getSymbol)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        delayedMarketPriceService::resolveForDisplay,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        BigDecimal totalInvestedCost = positions.stream()
                .map(PaperPosition::getTotalCost)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pricedInvestedCost = positions.stream()
                .filter(position -> hasExecutableDelayedPrice(delayedPriceBySymbol.get(position.getSymbol())))
                .map(PaperPosition::getTotalCost)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedMarketValue = positions.stream()
                .filter(position -> hasExecutableDelayedPrice(delayedPriceBySymbol.get(position.getSymbol())))
                .map(position -> marketValue(position, delayedPriceBySymbol.get(position.getSymbol())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPortfolioValue = money(account.getCashBalance().add(estimatedMarketValue));
        BigDecimal unrealizedProfitLoss = money(estimatedMarketValue.subtract(pricedInvestedCost));
        BigDecimal realizedProfitLoss = money(transactionRepository.sumCurrentSessionRealizedProfitLossByUserIdAndSide(
                account.getUser().getUserId(),
                PaperTradeSide.SELL
        ));
        BigDecimal totalProfitLoss = money(realizedProfitLoss.add(unrealizedProfitLoss));
        BigDecimal totalProfitLossPercent = account.getStartingCash().compareTo(BigDecimal.ZERO) == 0
                ? percent(BigDecimal.ZERO)
                : percent(totalProfitLoss
                .multiply(BigDecimal.valueOf(100))
                .divide(account.getStartingCash(), PERCENT_SCALE, RoundingMode.HALF_UP));
        TodayProfitLoss todayProfitLoss = todayProfitLoss(account, positions, delayedPriceBySymbol);
        BigDecimal totalFeesPaid = money(transactionRepository.sumCurrentSessionFeeByUserIdAndSideIn(
                account.getUser().getUserId(),
                FEE_SIDES
        ));
        BigDecimal returnPercentage = totalProfitLossPercent;

        List<PaperPositionResponse> positionResponses = positions.stream()
                .map(position -> toPositionResponse(
                        position,
                        stockBySymbol.get(position.getSymbol()),
                        totalPortfolioValue,
                        delayedPriceBySymbol.get(position.getSymbol())
                ))
                .toList();
        int pricedPositionCount = (int) positions.stream()
                .filter(position -> hasExecutableDelayedPrice(delayedPriceBySymbol.get(position.getSymbol())))
                .count();
        int unpricedPositionCount = positions.size() - pricedPositionCount;
        boolean portfolioValuationComplete = unpricedPositionCount == 0;
        String portfolioDataNote = portfolioValuationComplete
                ? null
                : "Portfolio valuation is partial because some delayed stored prices are unavailable. Unpriced positions are excluded from market value, unrealized P/L, and return calculations.";

        return new PaperPortfolioResponse(
                account.getUser().getUserId(),
                money(account.getCashBalance()),
                money(account.getStartingCash()),
                money(totalInvestedCost),
                money(estimatedMarketValue),
                totalPortfolioValue,
                unrealizedProfitLoss,
                realizedProfitLoss,
                realizedProfitLoss,
                totalProfitLoss,
                totalProfitLossPercent,
                todayProfitLoss.todayOpenPositionProfitLoss(),
                todayProfitLoss.todayRealizedProfitLossAfterFees(),
                todayProfitLoss.todayProfitLoss(),
                todayProfitLoss.todayProfitLossPercent(),
                todayProfitLoss.complete(),
                todayProfitLoss.note(),
                returnPercentage,
                totalFeesPaid,
                currentSessionNumber(account),
                account.getLastResetAt(),
                positionResponses,
                pricedPositionCount,
                unpricedPositionCount,
                portfolioValuationComplete,
                portfolioDataNote
        );
    }

    private TodayProfitLoss todayProfitLoss(
            PaperTradingAccount account,
            List<PaperPosition> positions,
            Map<String, DelayedMarketPrice> delayedPriceBySymbol
    ) {
        BigDecimal todayOpenPositionProfitLoss = BigDecimal.ZERO;
        boolean complete = true;
        for (PaperPosition position : positions) {
            DelayedMarketPrice delayedPrice = delayedPriceBySymbol.get(position.getSymbol());
            if (!hasExecutableDelayedPrice(delayedPrice) || delayedPrice.displayedAbsoluteChange() == null) {
                complete = false;
                continue;
            }
            todayOpenPositionProfitLoss = todayOpenPositionProfitLoss.add(
                    delayedPrice.displayedAbsoluteChange().multiply(BigDecimal.valueOf(position.getQuantity()))
            );
        }

        LocalDate today = LocalDate.now(NEW_YORK_ZONE);
        LocalDateTime todayStart = newYorkDateBoundaryInTransactionStorageZone(today.atStartOfDay());
        LocalDateTime todayEnd = newYorkDateBoundaryInTransactionStorageZone(today.plusDays(1).atStartOfDay())
                .minusNanos(1);
        BigDecimal todayRealizedProfitLossAfterFees = money(
                transactionRepository.sumCurrentSessionRealizedProfitLossByUserIdAndSideAndExecutedAtBetween(
                        account.getUser().getUserId(),
                        PaperTradeSide.SELL,
                        todayStart,
                        todayEnd
                )
        );
        BigDecimal todayProfitLoss = money(todayOpenPositionProfitLoss.add(todayRealizedProfitLossAfterFees));
        BigDecimal todayProfitLossPercent = account.getStartingCash().compareTo(BigDecimal.ZERO) == 0
                ? percent(BigDecimal.ZERO)
                : percent(todayProfitLoss
                .multiply(BigDecimal.valueOf(100))
                .divide(account.getStartingCash(), PERCENT_SCALE, RoundingMode.HALF_UP));
        String note = complete
                ? TODAY_PL_NOTE
                : TODAY_PL_NOTE + " Some open positions were excluded because displayed price or previous close is unavailable.";
        return new TodayProfitLoss(
                money(todayOpenPositionProfitLoss),
                todayRealizedProfitLossAfterFees,
                todayProfitLoss,
                todayProfitLossPercent,
                complete,
                note
        );
    }

    private LocalDateTime newYorkDateBoundaryInTransactionStorageZone(LocalDateTime newYorkDateTime) {
        return newYorkDateTime.atZone(NEW_YORK_ZONE)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private PaperTradingAccountResponse toAccountResponse(PaperTradingAccount account) {
        return new PaperTradingAccountResponse(
                account.getAccountId(),
                money(account.getCashBalance()),
                money(account.getStartingCash()),
                currentSessionNumber(account),
                account.getLastResetAt(),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private PaperPositionResponse toPositionResponse(
            PaperPosition position,
            Stock stock,
            BigDecimal totalPortfolioValue,
            DelayedMarketPrice delayedPrice
    ) {
        boolean hasValuationPrice = hasExecutableDelayedPrice(delayedPrice);
        BigDecimal currentPrice = hasValuationPrice ? money(delayedPrice.displayedPrice()) : null;
        BigDecimal marketValue = hasValuationPrice
                ? money(currentPrice.multiply(BigDecimal.valueOf(position.getQuantity())))
                : null;
        BigDecimal investedCost = money(position.getTotalCost());
        BigDecimal unrealizedProfitLoss = hasValuationPrice ? money(marketValue.subtract(investedCost)) : null;
        BigDecimal unrealizedPercent = !hasValuationPrice
                ? null
                : investedCost.compareTo(BigDecimal.ZERO) == 0
                ? percent(BigDecimal.ZERO)
                : percent(unrealizedProfitLoss
                .multiply(BigDecimal.valueOf(100))
                .divide(investedCost, PERCENT_SCALE, RoundingMode.HALF_UP));
        BigDecimal weightPercent = !hasValuationPrice || totalPortfolioValue == null
                ? null
                : totalPortfolioValue.compareTo(BigDecimal.ZERO) == 0
                ? percent(BigDecimal.ZERO)
                : percent(marketValue
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalPortfolioValue, PERCENT_SCALE, RoundingMode.HALF_UP));
        LocalDateTime lastUpdated = delayedPrice != null && delayedPrice.lastBackendUpdatedAt() != null
                ? delayedPrice.lastBackendUpdatedAt()
                : stock == null || stock.getLastUpdated() == null ? stock == null ? null : stock.getUpdatedAt() : stock.getLastUpdated();
        return new PaperPositionResponse(
                position.getPositionId(),
                position.getSymbol(),
                stock == null ? StockMetadata.COMPANY_MAP.getOrDefault(position.getSymbol(), position.getSymbol()) : stock.getCompanyName(),
                position.getQuantity(),
                money(position.getAverageCost()),
                investedCost,
                investedCost,
                currentPrice,
                marketValue,
                unrealizedProfitLoss,
                unrealizedPercent,
                weightPercent,
                StockMetadata.RISK_CATEGORY_MAP.get(position.getSymbol()),
                lastUpdated,
                currentPrice,
                marketValue,
                delayedPrice == null ? "Delayed valuation price is unavailable." : delayedPrice.dataNote(),
                DelayedPriceMetadataResponse.from(delayedPrice)
        );
    }

    private PaperTradeTransactionResponse toTransactionResponse(PaperTradeTransaction transaction) {
        BigDecimal fee = money(transaction.getFee());
        BigDecimal netAmount = normalizedNetAmount(transaction);
        BigDecimal executionPrice = money(transaction.getExecutionPrice());
        return new PaperTradeTransactionResponse(
                transaction.getTransactionId(),
                transaction.getSymbol(),
                transaction.getSide().name(),
                transaction.getQuantity(),
                executionPrice,
                executionPrice,
                money(transaction.getGrossAmount()),
                fee,
                netAmount,
                netAmount,
                money(transaction.getRealizedProfitLoss()),
                money(transaction.getRealizedProfitLoss()),
                money(transaction.getCashBalanceAfter()),
                isCurrentSession(transaction),
                sessionNumber(transaction),
                transaction.getExecutedAt(),
                transaction.getExecutedAt()
        );
    }

    private BigDecimal marketValue(PaperPosition position, DelayedMarketPrice delayedPrice) {
        if (!hasExecutableDelayedPrice(delayedPrice)) {
            return money(BigDecimal.ZERO);
        }
        return money(delayedPrice.displayedPrice().multiply(BigDecimal.valueOf(position.getQuantity())));
    }

    private boolean hasExecutableDelayedPrice(DelayedMarketPrice delayedPrice) {
        return delayedPrice != null
                && delayedPrice.tradeExecutable()
                && delayedPrice.displayedPrice() != null
                && delayedPrice.displayedPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal normalizedNetAmount(PaperTradeTransaction transaction) {
        if (transaction.getNetAmount() != null) {
            return money(transaction.getNetAmount());
        }
        if (transaction.getSide() == PaperTradeSide.RESET) {
            return money(BigDecimal.ZERO);
        }
        return money(transaction.getGrossAmount());
    }

    private boolean isCurrentSession(PaperTradeTransaction transaction) {
        return transaction.getIsCurrentSession() == null || Boolean.TRUE.equals(transaction.getIsCurrentSession());
    }

    private int sessionNumber(PaperTradeTransaction transaction) {
        return transaction.getSessionNumber() == null || transaction.getSessionNumber() < 1
                ? 1
                : transaction.getSessionNumber();
    }

    private int currentSessionNumber(PaperTradingAccount account) {
        return account.getCurrentSessionNumber() == null || account.getCurrentSessionNumber() < 1
                ? 1
                : account.getCurrentSessionNumber();
    }

    private BigDecimal currentTradeFee() {
        return money(tradeFee == null ? BigDecimal.ONE : tradeFee);
    }

    private LocalDateTime parseDateTimeFilter(String value, boolean startOfDay) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed).atTime(startOfDay ? LocalTime.MIN : LocalTime.MAX);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Invalid date/time filter: " + value);
            }
        }
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_TRANSACTION_SIZE;
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        return Math.min(size, MAX_TRANSACTION_SIZE);
    }

    private void recalculateBehaviorAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recalculateBehaviorBestEffort(userId);
                }
            });
            return;
        }
        recalculateBehaviorBestEffort(userId);
    }

    private void recalculateBehaviorBestEffort(Long userId) {
        try {
            behaviorProfileService.recalculateBehaviorProfile(userId);
        } catch (RuntimeException exception) {
            log.warn("Paper-trading behavior recalculation failed after committed trade userId={}", userId, exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private record TransactionQuery(
            Long userId,
            String symbol,
            PaperTradeSide side,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size,
            boolean currentSessionOnly
    ) {
    }

    private record TodayProfitLoss(
            BigDecimal todayOpenPositionProfitLoss,
            BigDecimal todayRealizedProfitLossAfterFees,
            BigDecimal todayProfitLoss,
            BigDecimal todayProfitLossPercent,
            boolean complete,
            String note
    ) {
    }
}
