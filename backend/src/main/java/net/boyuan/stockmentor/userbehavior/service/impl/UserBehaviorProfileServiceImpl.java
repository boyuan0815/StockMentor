package net.boyuan.stockmentor.userbehavior.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.papertrading.entity.PaperPosition;
import net.boyuan.stockmentor.papertrading.entity.PaperTradeTransaction;
import net.boyuan.stockmentor.papertrading.entity.PaperTradingAccount;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
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
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBehaviorProfileServiceImpl implements UserBehaviorProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserBehaviorProfileServiceImpl.class);
    private static final String LOW_CONFIDENCE_NOTE = "Paper-trading behavior has LOW confidence and should be treated as informational only.";
    private static final int ANALYSIS_WINDOW_DAYS = 30;
    private static final String ANALYSIS_TIMEFRAME = "7D";

    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final AppUserRepository appUserRepository;
    private final PaperTradeTransactionRepository transactionRepository;
    private final PaperPositionRepository positionRepository;
    private final StockRepository stockRepository;
    private final StockAnalysisSnapshotRepository snapshotRepository;
    private final PaperTradingAccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserBehaviorProfile> getLatestBehaviorProfile(Long userId) {
        return behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId);
    }

    @Override
    @Transactional
    public UserBehaviorProfile createLowConfidenceProfileIfMissing(AppUser user) {
        Optional<UserBehaviorProfile> latestProfile = behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(user.getUserId());
        if (latestProfile.isPresent()) {
            log.debug("Reusing existing behavior profile for userId={} behaviorProfileId={}",
                    user.getUserId(), latestProfile.get().getBehaviorProfileId());
            return latestProfile.get();
        }

        LocalDateTime now = LocalDateTime.now();
        UserBehaviorProfile profile = new UserBehaviorProfile();
        profile.setUser(user);
        profile.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        profile.setBehaviorConfidence(BehaviorConfidence.LOW);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        UserBehaviorProfile saved = behaviorProfileRepository.save(profile);
        log.info("Created LOW confidence behavior profile for userId={}, behaviorProfileId={}", user.getUserId(), saved.getBehaviorProfileId());
        return saved;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserBehaviorProfile recalculateBehaviorProfile(Long userId) {
        AppUser user = appUserRepository.findByUserIdAndStatusAndIsDeletedFalse(userId, AppUserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active user not found for behavior profile"));
        LocalDate analysisEnd = LocalDate.now();
        LocalDate analysisStart = analysisEnd.minusDays(ANALYSIS_WINDOW_DAYS - 1L);
        LocalDateTime startAt = analysisStart.atStartOfDay();
        LocalDateTime endAt = analysisEnd.atTime(LocalTime.MAX);
        List<PaperTradeTransaction> transactions = transactionRepository
                .findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(userId, startAt, endAt).stream()
                .filter(this::isBehaviorTransaction)
                .toList();
        List<PaperPosition> positions = positionRepository.findByUserUserId(userId);
        Set<String> symbols = symbols(transactions, positions);
        Map<String, StockAnalysisSnapshot> latestSnapshotBySymbol = latestSnapshots(symbols);
        Map<String, Stock> stockBySymbol = stocksBySymbol(symbols(positions));
        Optional<PaperTradingAccount> account = accountRepository.findByUserUserId(userId);
        UserBehaviorProfile profile = behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> {
                    UserBehaviorProfile created = new UserBehaviorProfile();
                    created.setUser(user);
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });

        applyBehaviorMetrics(profile, user, analysisStart, analysisEnd, transactions, positions, latestSnapshotBySymbol, stockBySymbol, account);
        UserBehaviorProfile saved = behaviorProfileRepository.save(profile);
        log.info("Recalculated paper-trading behavior profile userId={}, behaviorProfileId={}, confidence={}, style={}",
                userId, saved.getBehaviorProfileId(), saved.getBehaviorConfidence(), saved.getBehaviorStyle());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public BehaviorSummaryForSuggestion getBehaviorSummaryForSuggestion(Long userId) {
        return behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                .map(this::toSummary)
                .orElseGet(() -> new BehaviorSummaryForSuggestion(
                        null,
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
                        "No paper-trading behavior profile has been calculated yet.",
                        null,
                        "Behavior profile is unavailable; no paper-trading transaction source exists yet."
                ));
    }

    private BehaviorSummaryForSuggestion toSummary(UserBehaviorProfile profile) {
        return new BehaviorSummaryForSuggestion(
                profile.getBehaviorProfileId(),
                profile.getAnalysisStartDate(),
                profile.getAnalysisEndDate(),
                profile.getBehaviorRiskScore(),
                profile.getBehaviorStyle(),
                profile.getBehaviorConfidence(),
                profile.getAveragePositionSizePercent(),
                profile.getTurnoverLevel(),
                profile.getConcentrationLevel(),
                profile.getHighVolatilityExposure(),
                profile.getStockRiskExposureScore(),
                profile.getConcentrationScore(),
                profile.getTurnoverScore(),
                profile.getHoldingPeriodScore(),
                profile.getVolatilityExposureScore(),
                profile.getFavoriteRiskCategory(),
                profile.getMostTradedSymbols(),
                profile.getBehaviorSummaryText(),
                profile.getUpdatedAt(),
                profile.getBehaviorConfidence() == BehaviorConfidence.LOW
                        ? LOW_CONFIDENCE_NOTE
                        : "Paper-trading behavior is calculated from recent simulated trades only."
        );
    }

    private void applyBehaviorMetrics(
            UserBehaviorProfile profile,
            AppUser user,
            LocalDate analysisStart,
            LocalDate analysisEnd,
            List<PaperTradeTransaction> transactions,
            List<PaperPosition> positions,
            Map<String, StockAnalysisSnapshot> latestSnapshotBySymbol,
            Map<String, Stock> stockBySymbol,
            Optional<PaperTradingAccount> account
    ) {
        LocalDateTime now = LocalDateTime.now();
        profile.setUser(user);
        profile.setAnalysisStartDate(analysisStart);
        profile.setAnalysisEndDate(analysisEnd);
        profile.setUpdatedAt(now);
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }

        int transactionCount = transactions.size();
        long distinctSymbols = transactions.stream().map(PaperTradeTransaction::getSymbol).distinct().count();
        BehaviorConfidence confidence = confidence(transactionCount, distinctSymbols);
        profile.setBehaviorConfidence(confidence);

        RiskMetrics riskMetrics = riskMetrics(transactions, latestSnapshotBySymbol);
        VolatilityMetrics volatilityMetrics = volatilityMetrics(transactions, latestSnapshotBySymbol);
        PositionMetrics positionMetrics = positionMetrics(positions, stockBySymbol, account);
        String mostTradedSymbols = mostTradedSymbols(transactions);
        int turnoverScore = turnoverScore(transactions, transactionCount, positionMetrics.accountEquity());
        Integer behaviorRiskScore = behaviorRiskScore(
                riskMetrics.score(),
                volatilityMetrics.score(),
                turnoverScore,
                positionMetrics.concentrationScore()
        );

        if (riskMetrics.hasBuyData()) {
            profile.setStockRiskExposureScore(riskMetrics.score());
            profile.setFavoriteRiskCategory(riskMetrics.favoriteRiskCategory());
        } else {
            profile.setStockRiskExposureScore(null);
            profile.setFavoriteRiskCategory(null);
        }

        profile.setVolatilityExposureScore(volatilityMetrics.score());
        profile.setHighVolatilityExposure(volatilityMetrics.score() == null ? null : highVolatilityExposure(volatilityMetrics.score()));
        profile.setBehaviorRiskScore(behaviorRiskScore);
        profile.setBehaviorStyle(behaviorRiskScore == null
                ? UserBehaviorStyle.INSUFFICIENT_DATA
                : behaviorStyle(behaviorRiskScore, confidence, transactionCount));

        if (confidence == BehaviorConfidence.LOW) {
            profile.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        }

        profile.setMostTradedSymbols(mostTradedSymbols);
        profile.setAveragePositionSizePercent(positionMetrics.averagePositionSizePercent());
        profile.setConcentrationScore(positionMetrics.concentrationScore());
        profile.setConcentrationLevel(positionMetrics.concentrationLevel());
        profile.setTurnoverScore(turnoverScore);
        profile.setTurnoverLevel(turnoverLevel(turnoverScore));
        profile.setHoldingPeriodScore(null);
        profile.setBehaviorSummaryText(behaviorSummaryText(profile, transactionCount, distinctSymbols));
    }

    private BehaviorConfidence confidence(int transactionCount, long distinctSymbols) {
        if (transactionCount >= 10 && distinctSymbols >= 3) {
            return BehaviorConfidence.HIGH;
        }
        if (transactionCount >= 3 && distinctSymbols >= 2) {
            return BehaviorConfidence.MEDIUM;
        }
        return BehaviorConfidence.LOW;
    }

    private RiskMetrics riskMetrics(
            List<PaperTradeTransaction> transactions,
            Map<String, StockAnalysisSnapshot> latestSnapshotBySymbol
    ) {
        BigDecimal weightedRisk = BigDecimal.ZERO;
        BigDecimal totalBuyGross = BigDecimal.ZERO;
        Map<String, BigDecimal> grossByRiskCategory = new LinkedHashMap<>();
        for (PaperTradeTransaction transaction : transactions) {
            if (transaction.getSide() != PaperTradeSide.BUY) {
                continue;
            }
            BigDecimal grossAmount = transaction.getGrossAmount() == null ? BigDecimal.ZERO : transaction.getGrossAmount();
            if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String riskCategory = riskCategory(transaction.getSymbol(), latestSnapshotBySymbol);
            if (riskCategory == null) {
                continue;
            }
            int riskWeight = riskWeight(riskCategory);
            weightedRisk = weightedRisk.add(grossAmount.multiply(BigDecimal.valueOf(riskWeight)));
            totalBuyGross = totalBuyGross.add(grossAmount);
            grossByRiskCategory.merge(riskCategory, grossAmount, BigDecimal::add);
        }
        if (totalBuyGross.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskMetrics(false, null, null);
        }
        int score = clampScore(weightedRisk.divide(totalBuyGross, 0, RoundingMode.HALF_UP).intValue());
        String favoriteRiskCategory = grossByRiskCategory.entrySet().stream()
                .max(Map.Entry.<String, BigDecimal>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
        return new RiskMetrics(true, score, favoriteRiskCategory);
    }

    private VolatilityMetrics volatilityMetrics(
            List<PaperTradeTransaction> transactions,
            Map<String, StockAnalysisSnapshot> latestSnapshotBySymbol
    ) {
        BigDecimal weightedVolatility = BigDecimal.ZERO;
        BigDecimal totalBuyGross = BigDecimal.ZERO;
        for (PaperTradeTransaction transaction : transactions) {
            if (transaction.getSide() != PaperTradeSide.BUY) {
                continue;
            }
            BigDecimal grossAmount = transaction.getGrossAmount() == null ? BigDecimal.ZERO : transaction.getGrossAmount();
            if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Integer volatilityWeight = volatilityWeight(transaction.getSymbol(), latestSnapshotBySymbol);
            if (volatilityWeight == null) {
                continue;
            }
            weightedVolatility = weightedVolatility.add(grossAmount.multiply(BigDecimal.valueOf(volatilityWeight)));
            totalBuyGross = totalBuyGross.add(grossAmount);
        }
        if (totalBuyGross.compareTo(BigDecimal.ZERO) <= 0) {
            return new VolatilityMetrics(null);
        }
        int score = weightedVolatility.divide(totalBuyGross, 0, RoundingMode.HALF_UP).intValue();
        return new VolatilityMetrics(clampScore(score));
    }

    private String mostTradedSymbols(List<PaperTradeTransaction> transactions) {
        if (transactions.isEmpty()) {
            return null;
        }
        Map<String, SymbolTradeMetrics> metricsBySymbol = transactions.stream()
                .collect(Collectors.toMap(
                        PaperTradeTransaction::getSymbol,
                        transaction -> new SymbolTradeMetrics(
                                1,
                                transaction.getGrossAmount() == null ? BigDecimal.ZERO : transaction.getGrossAmount()
                        ),
                        SymbolTradeMetrics::merge
                ));
        String symbols = metricsBySymbol.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, SymbolTradeMetrics>>comparingInt(entry -> entry.getValue().transactionCount())
                        .reversed()
                        .thenComparing((first, second) -> second.getValue().grossAmount().compareTo(first.getValue().grossAmount()))
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        return symbols.isBlank() ? null : symbols;
    }

    private PositionMetrics positionMetrics(
            List<PaperPosition> positions,
            Map<String, Stock> stockBySymbol,
            Optional<PaperTradingAccount> account
    ) {
        List<BigDecimal> positionValues = positions.stream()
                .filter(position -> position.getQuantity() != null && position.getQuantity() > 0)
                .map(position -> positionValue(position, stockBySymbol.get(position.getSymbol())))
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        BigDecimal totalPositionValue = positionValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal accountEquity = accountEquity(account, totalPositionValue);
        if (totalPositionValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new PositionMetrics(null, null, null, accountEquity);
        }
        BigDecimal largest = positionValues.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int concentrationScore = largest.multiply(BigDecimal.valueOf(100))
                .divide(totalPositionValue, 0, RoundingMode.HALF_UP)
                .intValue();
        BigDecimal averagePositionSize = averagePositionSizePercent(positionValues, accountEquity);
        return new PositionMetrics(
                averagePositionSize,
                clampScore(concentrationScore),
                concentrationLevel(concentrationScore),
                accountEquity
        );
    }

    private BigDecimal averagePositionSizePercent(
            List<BigDecimal> positionValues,
            BigDecimal accountEquity
    ) {
        if (accountEquity == null || accountEquity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal totalPercent = positionValues.stream()
                .map(value -> value.multiply(BigDecimal.valueOf(100)).divide(accountEquity, 6, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalPercent.divide(BigDecimal.valueOf(positionValues.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal accountEquity(Optional<PaperTradingAccount> account, BigDecimal totalPositionValue) {
        if (account.isEmpty() || account.get().getCashBalance() == null) {
            return null;
        }

        BigDecimal safePositionValue = totalPositionValue == null
                ? BigDecimal.ZERO
                : totalPositionValue.max(BigDecimal.ZERO);

        BigDecimal cashBalance = account.get().getCashBalance();

        BigDecimal equity = cashBalance.add(safePositionValue);

        return equity.compareTo(BigDecimal.ZERO) > 0 ? equity : null;
    }

    private Collection<String> symbols(List<PaperPosition> positions) {
        return positions.stream()
                .map(PaperPosition::getSymbol)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Set<String> symbols(List<PaperTradeTransaction> transactions, List<PaperPosition> positions) {
        Set<String> symbols = new HashSet<>();
        transactions.stream().map(PaperTradeTransaction::getSymbol).filter(Objects::nonNull).forEach(symbols::add);
        positions.stream().map(PaperPosition::getSymbol).filter(Objects::nonNull).forEach(symbols::add);
        return symbols;
    }

    private boolean isBehaviorTransaction(PaperTradeTransaction transaction) {
        if (transaction.getSymbol() == null || transaction.getSymbol().isBlank()) {
            return false;
        }
        if (transaction.getQuantity() == null || transaction.getQuantity() <= 0) {
            return false;
        }
        if (transaction.getSide() != PaperTradeSide.BUY && transaction.getSide() != PaperTradeSide.SELL) {
            return false;
        }
        return transaction.getIsCurrentSession() == null || Boolean.TRUE.equals(transaction.getIsCurrentSession());
    }

    private Map<String, StockAnalysisSnapshot> latestSnapshots(Set<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, StockAnalysisSnapshot> latestBySymbol = new LinkedHashMap<>();
        for (StockAnalysisSnapshot snapshot : snapshotRepository.findBySymbolInAndTimeframeOrderByCreatedAtDescAnalysisSnapshotIdDesc(symbols, ANALYSIS_TIMEFRAME)) {
            latestBySymbol.putIfAbsent(snapshot.getSymbol(), snapshot);
        }
        return latestBySymbol;
    }

    private Map<String, Stock> stocksBySymbol(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        return stockRepository.findBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
    }

    private BigDecimal positionValue(PaperPosition position, Stock stock) {
        if (stock != null && stock.getCurrentPrice() != null && stock.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            return stock.getCurrentPrice().multiply(BigDecimal.valueOf(position.getQuantity()));
        }
        if (position.getTotalCost() != null && position.getTotalCost().compareTo(BigDecimal.ZERO) > 0) {
            return position.getTotalCost();
        }
        return BigDecimal.ZERO;
    }

    private int riskWeight(String riskCategory) {
        return switch (riskCategory) {
            case "conservative" -> 25;
            case "moderate" -> 55;
            case "moderate_aggressive" -> 70;
            case "aggressive" -> 85;
            default -> throw new IllegalArgumentException("Unsupported risk category for behavior scoring: " + riskCategory);
        };
    }

    private String riskCategory(String symbol, Map<String, StockAnalysisSnapshot> latestSnapshotBySymbol) {
        StockAnalysisSnapshot snapshot = latestSnapshotBySymbol.get(symbol);
        String snapshotCategory = snapshot == null ? null : normalizeRiskCategory(snapshot.getRiskCategory());
        if (snapshotCategory != null) {
            return snapshotCategory;
        }
        return normalizeRiskCategory(StockMetadata.RISK_CATEGORY_MAP.get(symbol));
    }

    private String normalizeRiskCategory(String value) {
        String normalized = normalizeLabel(value);
        return switch (normalized) {
            case "conservative", "moderate", "moderate_aggressive", "aggressive" -> normalized;
            default -> null;
        };
    }

    private Integer volatilityWeight(String symbol, Map<String, StockAnalysisSnapshot> latestSnapshotBySymbol) {
        StockAnalysisSnapshot snapshot = latestSnapshotBySymbol.get(symbol);
        if (snapshot == null) {
            return null;
        }
        return switch (normalizeLabel(snapshot.getVolatilityLabel())) {
            case "very_low" -> 20;
            case "low" -> 35;
            case "medium", "moderate" -> 55;
            case "high" -> 75;
            case "very_high" -> 90;
            default -> null;
        };
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replace('-', '_')
                .replace(' ', '_');
    }

    private int turnoverScore(
            List<PaperTradeTransaction> transactions,
            int transactionCount,
            BigDecimal accountEquity
    ) {
        if (accountEquity == null || accountEquity.compareTo(BigDecimal.ZERO) <= 0) {
            return Math.min(100, transactionCount * 10);
        }
        BigDecimal totalTradeGross = transactions.stream()
                .map(PaperTradeTransaction::getGrossAmount)
                .filter(Objects::nonNull)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int score = totalTradeGross.multiply(BigDecimal.valueOf(100))
                .divide(accountEquity, 0, RoundingMode.HALF_UP)
                .intValue();
        return clampScore(score);
    }

    private TurnoverLevel turnoverLevel(int turnoverScore) {
        if (turnoverScore >= 71) {
            return TurnoverLevel.HIGH;
        }
        if (turnoverScore >= 31) {
            return TurnoverLevel.MEDIUM;
        }
        return TurnoverLevel.LOW;
    }

    private Integer behaviorRiskScore(
            Integer stockRiskExposureScore,
            Integer volatilityExposureScore,
            Integer turnoverScore,
            Integer concentrationScore
    ) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal usedWeight = BigDecimal.ZERO;
        WeightedScore[] scores = {
                new WeightedScore(stockRiskExposureScore, new BigDecimal("0.40")),
                new WeightedScore(volatilityExposureScore, new BigDecimal("0.25")),
                new WeightedScore(turnoverScore, new BigDecimal("0.20")),
                new WeightedScore(concentrationScore, new BigDecimal("0.15"))
        };
        for (WeightedScore score : scores) {
            if (score.value() == null) {
                continue;
            }
            weightedTotal = weightedTotal.add(BigDecimal.valueOf(score.value()).multiply(score.weight()));
            usedWeight = usedWeight.add(score.weight());
        }
        if (usedWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        int composite = weightedTotal.divide(usedWeight, 0, RoundingMode.HALF_UP).intValue();
        return clampScore(composite);
    }

    private ConcentrationLevel concentrationLevel(int score) {
        if (score >= 71) {
            return ConcentrationLevel.CONCENTRATED;
        }
        if (score >= 41) {
            return ConcentrationLevel.MODERATE;
        }
        return ConcentrationLevel.DIVERSIFIED;
    }

    private HighVolatilityExposure highVolatilityExposure(int riskScore) {
        if (riskScore >= 70) {
            return HighVolatilityExposure.HIGH;
        }
        if (riskScore >= 45) {
            return HighVolatilityExposure.MEDIUM;
        }
        return HighVolatilityExposure.LOW;
    }

    private UserBehaviorStyle behaviorStyle(int riskScore, BehaviorConfidence confidence, int transactionCount) {
        if (riskScore >= 75) {
            return UserBehaviorStyle.AGGRESSIVE;
        }
        if (riskScore >= 60 || (confidence == BehaviorConfidence.HIGH && transactionCount >= 10)) {
            return UserBehaviorStyle.ACTIVE_TRADER;
        }
        if (riskScore >= 40) {
            return UserBehaviorStyle.BALANCED;
        }
        return UserBehaviorStyle.CONSERVATIVE;
    }

    private String behaviorSummaryText(UserBehaviorProfile profile, int transactionCount, long distinctSymbols) {
        if (transactionCount == 0 || profile.getBehaviorConfidence() == BehaviorConfidence.LOW) {
            return "Recent paper-trading activity is still limited, so behavior signals remain low confidence.";
        }

        String riskText = profile.getFavoriteRiskCategory() == null
                ? "no clear BUY risk preference"
                : buyRiskPreferenceText(profile.getFavoriteRiskCategory());
        String symbolsText = profile.getMostTradedSymbols() == null
                ? "supported stocks"
                : profile.getMostTradedSymbols();
        String turnoverText = profile.getTurnoverLevel() == null
                ? "unknown turnover"
                : profile.getTurnoverLevel().name().toLowerCase() + " turnover";
        String concentrationText = profile.getConcentrationLevel() == null
                ? "unknown concentration"
                : concentrationText(profile.getConcentrationLevel());
        String volatilityText = profile.getHighVolatilityExposure() == null
                ? "unknown volatility exposure"
                : profile.getHighVolatilityExposure().name().toLowerCase() + " volatility exposure";
        return "Recent paper trades show "
                + profile.getBehaviorConfidence().name().toLowerCase()
                + " confidence behavior across "
                + distinctSymbols
                + " symbols, with most activity in "
                + symbolsText
                + ", "
                + turnoverText
                + ", "
                + concentrationText
                + ", "
                + volatilityText
                + ", and "
                + riskText
                + " based on BUY activity.";
    }

    private String buyRiskPreferenceText(String favoriteRiskCategory) {
        String normalized = normalizeLabel(favoriteRiskCategory).replace('_', '-');
        String article = normalized.startsWith("a") ? "an" : "a";
        return article + " " + normalized + " BUY-risk preference";
    }

    private String concentrationText(ConcentrationLevel concentrationLevel) {
        return switch (concentrationLevel) {
            case DIVERSIFIED -> "diversified holdings";
            case MODERATE -> "moderate concentration";
            case CONCENTRATED -> "concentrated holdings";
        };
    }

    private record RiskMetrics(boolean hasBuyData, Integer score, String favoriteRiskCategory) {
    }

    private record VolatilityMetrics(Integer score) {
    }

    private record PositionMetrics(
            BigDecimal averagePositionSizePercent,
            Integer concentrationScore,
            ConcentrationLevel concentrationLevel,
            BigDecimal accountEquity
    ) {
    }

    private record SymbolTradeMetrics(int transactionCount, BigDecimal grossAmount) {
        private SymbolTradeMetrics merge(SymbolTradeMetrics other) {
            return new SymbolTradeMetrics(
                    transactionCount + other.transactionCount,
                    grossAmount.add(other.grossAmount)
            );
        }
    }

    private record WeightedScore(Integer value, BigDecimal weight) {
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
