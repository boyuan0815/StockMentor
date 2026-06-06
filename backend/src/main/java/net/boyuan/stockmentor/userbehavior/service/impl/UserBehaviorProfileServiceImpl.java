package net.boyuan.stockmentor.userbehavior.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.common.util.StockMetadata;
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
import net.boyuan.stockmentor.userbehavior.model.HighVolatilityExposure;
import net.boyuan.stockmentor.userbehavior.model.TurnoverLevel;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBehaviorProfileServiceImpl implements UserBehaviorProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserBehaviorProfileServiceImpl.class);
    private static final String LOW_CONFIDENCE_NOTE = "Paper-trading behavior has LOW confidence and should be treated as informational only.";
    private static final int ANALYSIS_WINDOW_DAYS = 30;

    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final AppUserRepository appUserRepository;
    private final PaperTradeTransactionRepository transactionRepository;
    private final PaperPositionRepository positionRepository;
    private final StockRepository stockRepository;

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
    @Transactional
    public UserBehaviorProfile recalculateBehaviorProfile(Long userId) {
        AppUser user = appUserRepository.findByUserIdAndStatusAndIsDeletedFalse(userId, AppUserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active user not found for behavior profile"));
        LocalDate analysisEnd = LocalDate.now();
        LocalDate analysisStart = analysisEnd.minusDays(ANALYSIS_WINDOW_DAYS - 1L);
        LocalDateTime startAt = analysisStart.atStartOfDay();
        LocalDateTime endAt = analysisEnd.atTime(LocalTime.MAX);
        List<PaperTradeTransaction> transactions = transactionRepository
                .findByUserUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(userId, startAt, endAt);
        UserBehaviorProfile profile = behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> {
                    UserBehaviorProfile created = new UserBehaviorProfile();
                    created.setUser(user);
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });

        applyBehaviorMetrics(profile, user, analysisStart, analysisEnd, transactions);
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
            List<PaperTradeTransaction> transactions
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

        RiskMetrics riskMetrics = riskMetrics(transactions);
        PositionMetrics positionMetrics = positionMetrics(user.getUserId());
        String mostTradedSymbols = mostTradedSymbols(transactions);
        int turnoverScore = Math.min(100, transactionCount * 10);

        if (riskMetrics.hasBuyData()) {
            profile.setStockRiskExposureScore(riskMetrics.score());
            profile.setBehaviorRiskScore(riskMetrics.score());
            profile.setVolatilityExposureScore(riskMetrics.score());
            profile.setHighVolatilityExposure(highVolatilityExposure(riskMetrics.score()));
            profile.setBehaviorStyle(behaviorStyle(riskMetrics.score(), confidence, transactionCount));
            profile.setFavoriteRiskCategory(riskMetrics.favoriteRiskCategory());
        } else {
            profile.setStockRiskExposureScore(null);
            profile.setBehaviorRiskScore(null);
            profile.setVolatilityExposureScore(null);
            profile.setHighVolatilityExposure(null);
            profile.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
            profile.setFavoriteRiskCategory(null);
        }

        if (confidence == BehaviorConfidence.LOW) {
            profile.setBehaviorStyle(UserBehaviorStyle.INSUFFICIENT_DATA);
        }

        profile.setMostTradedSymbols(mostTradedSymbols);
        profile.setAveragePositionSizePercent(positionMetrics.averagePositionSizePercent());
        profile.setConcentrationScore(positionMetrics.concentrationScore());
        profile.setConcentrationLevel(positionMetrics.concentrationLevel());
        profile.setTurnoverScore(turnoverScore);
        profile.setTurnoverLevel(turnoverLevel(transactionCount));
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

    private RiskMetrics riskMetrics(List<PaperTradeTransaction> transactions) {
        BigDecimal weightedRisk = BigDecimal.ZERO;
        BigDecimal totalBuyGross = BigDecimal.ZERO;
        Map<String, BigDecimal> grossByRiskCategory = transactions.stream()
                .filter(transaction -> transaction.getSide() == PaperTradeSide.BUY)
                .filter(transaction -> transaction.getGrossAmount() != null)
                .filter(transaction -> transaction.getGrossAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        transaction -> riskCategory(transaction.getSymbol()),
                        Collectors.reducing(BigDecimal.ZERO, PaperTradeTransaction::getGrossAmount, BigDecimal::add)
                ));
        for (PaperTradeTransaction transaction : transactions) {
            if (transaction.getSide() != PaperTradeSide.BUY) {
                continue;
            }
            BigDecimal grossAmount = transaction.getGrossAmount() == null ? BigDecimal.ZERO : transaction.getGrossAmount();
            if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int riskWeight = riskWeight(transaction.getSymbol());
            weightedRisk = weightedRisk.add(grossAmount.multiply(BigDecimal.valueOf(riskWeight)));
            totalBuyGross = totalBuyGross.add(grossAmount);
        }
        if (totalBuyGross.compareTo(BigDecimal.ZERO) <= 0) {
            return new RiskMetrics(false, null, null);
        }
        int score = weightedRisk.divide(totalBuyGross, 0, RoundingMode.HALF_UP).intValue();
        String favoriteRiskCategory = grossByRiskCategory.entrySet().stream()
                .max(Map.Entry.<String, BigDecimal>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
        return new RiskMetrics(true, score, favoriteRiskCategory);
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

    private PositionMetrics positionMetrics(Long userId) {
        List<PaperPosition> positions = positionRepository.findByUserUserId(userId);
        if (positions.isEmpty()) {
            return new PositionMetrics(null, 0, null);
        }
        Map<String, Stock> stockBySymbol = stockRepository.findBySymbolIn(symbols(positions)).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
        List<BigDecimal> marketValues = positions.stream()
                .map(position -> marketValue(position, stockBySymbol.get(position.getSymbol())))
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        BigDecimal totalMarketValue = marketValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new PositionMetrics(null, 0, null);
        }
        BigDecimal largest = marketValues.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int concentrationScore = largest.multiply(BigDecimal.valueOf(100))
                .divide(totalMarketValue, 0, RoundingMode.HALF_UP)
                .intValue();
        BigDecimal averagePositionSize = BigDecimal.valueOf(100)
                .divide(BigDecimal.valueOf(marketValues.size()), 2, RoundingMode.HALF_UP);
        return new PositionMetrics(
                averagePositionSize,
                concentrationScore,
                concentrationLevel(concentrationScore)
        );
    }

    private Collection<String> symbols(List<PaperPosition> positions) {
        return positions.stream().map(PaperPosition::getSymbol).distinct().toList();
    }

    private BigDecimal marketValue(PaperPosition position, Stock stock) {
        if (stock == null || stock.getCurrentPrice() == null || stock.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return stock.getCurrentPrice().multiply(BigDecimal.valueOf(position.getQuantity()));
    }

    private int riskWeight(String symbol) {
        return switch (riskCategory(symbol)) {
            case "conservative" -> 25;
            case "aggressive" -> 85;
            default -> 55;
        };
    }

    private String riskCategory(String symbol) {
        return StockMetadata.RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate").toLowerCase();
    }

    private TurnoverLevel turnoverLevel(int transactionCount) {
        if (transactionCount >= 10) {
            return TurnoverLevel.HIGH;
        }
        if (transactionCount >= 3) {
            return TurnoverLevel.MEDIUM;
        }
        return TurnoverLevel.LOW;
    }

    private ConcentrationLevel concentrationLevel(int score) {
        if (score >= 70) {
            return ConcentrationLevel.CONCENTRATED;
        }
        if (score >= 40) {
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
                ? "mixed-risk"
                : profile.getFavoriteRiskCategory();
        String symbolsText = profile.getMostTradedSymbols() == null
                ? "supported stocks"
                : profile.getMostTradedSymbols();
        return "Recent paper trades show "
                + profile.getBehaviorConfidence().name().toLowerCase()
                + " confidence behavior across "
                + distinctSymbols
                + " symbols, with most activity in "
                + symbolsText
                + " and a "
                + riskText
                + " risk preference.";
    }

    private record RiskMetrics(boolean hasBuyData, Integer score, String favoriteRiskCategory) {
    }

    private record PositionMetrics(
            BigDecimal averagePositionSizePercent,
            Integer concentrationScore,
            ConcentrationLevel concentrationLevel
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
}
