package net.boyuan.stockmentor.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.*;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionBatch;
import net.boyuan.stockmentor.ai.entity.StockAiSuggestionItem;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionBatchStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionItemStatus;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionBatchRepository;
import net.boyuan.stockmentor.ai.repository.StockAiSuggestionItemRepository;
import net.boyuan.stockmentor.ai.service.OpenAiClient;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.analysis.entity.StockAnalysisSnapshot;
import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.analysis.service.StockAnalysisService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.common.util.StockMetadata;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import net.boyuan.stockmentor.userprofile.model.PreferredVolatility;
import net.boyuan.stockmentor.userprofile.model.RiskTolerance;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockAiSuggestionServiceImpl implements StockAiSuggestionService {
    private final CurrentUserService currentUserService;
    private final UserInvestmentProfileRepository profileRepository;
    private final StockAiSuggestionBatchRepository batchRepository;
    private final StockAiSuggestionItemRepository itemRepository;
    private final UserWatchlistRepository watchlistRepository;
    private final StockRepository stockRepository;
    private final StockAnalysisSnapshotRepository snapshotRepository;
    private final StockAnalysisService stockAnalysisService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_VERSION = "stock-suggestion-v1";
    private static final String ANALYSIS_TIMEFRAME = "7D";
    private static final int MAX_SUGGESTIONS = 3;
    private static final int MANUAL_REFRESH_COOLDOWN_HOURS = 1;
    private static final int BATCH_EXPIRY_HOURS = 24;
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.stream(StockMetadata.SYMBOLS.split(","))
            .map(String::trim)
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .toList();
    private static final List<StockAiSuggestionBatchStatus> READABLE_BATCH_STATUSES = List.of(
            StockAiSuggestionBatchStatus.SUCCESS,
            StockAiSuggestionBatchStatus.FALLBACK_CACHED,
            StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
    );
    private static final List<StockAiSuggestionItemStatus> TOP_ITEM_STATUSES = List.of(
            StockAiSuggestionItemStatus.ACTIVE,
            StockAiSuggestionItemStatus.WATCHLISTED
    );
    private static final String SYSTEM_PROMPT = """
            You are a beginner-friendly educational paper-trading assistant.

            Rank up to 3 supported stocks using only the structured backend data provided.
            Do not use external news, sectors, future predictions, or buy/sell advice.
            Return only valid JSON with this shape:
            {
              "batchSummary": "string",
              "suggestedStocks": [
                {
                  "symbol": "MSFT",
                  "rankNo": 1,
                  "matchScore": 85,
                  "riskLevel": "moderate",
                  "suggestionLabel": "Smooth trend learning",
                  "shortReason": "One sentence reason.",
                  "detailReason": "Beginner-friendly explanation using at least two provided factors."
                }
              ]
            }
            """;

    @Override
    @Transactional(readOnly = true)
    public StockAiSuggestionResponse getSuggestionsForCurrentUser() {
        AppUser user = currentUserService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        Optional<StockAiSuggestionBatch> latestBatch = batchRepository
                .findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getUserId(),
                        READABLE_BATCH_STATUSES,
                        now
                );

        return latestBatch
                .map(batch -> buildResponse(user, batch, "Returned stored AI stock suggestions", false))
                .orElseGet(() -> buildEmptyResponse(
                        user,
                        null,
                        "AI stock suggestions are not available yet. Please refresh suggestions when you are ready."
                ));
    }

    @Override
    @Transactional
    public StockAiSuggestionResponse refreshSuggestionsForCurrentUser() {
        AppUser user = currentUserService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        Optional<UserInvestmentProfile> profileOptional = profileRepository
                .findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId());

        if (profileOptional.isEmpty()) {
            return buildEmptyResponse(user, null, "Please complete onboarding before requesting AI stock suggestions.");
        }

        UserInvestmentProfile profile = profileOptional.get();
        Optional<StockAiSuggestionBatch> latestBatch = batchRepository
                .findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getUserId(),
                        READABLE_BATCH_STATUSES,
                        now
                );
        Optional<StockAiSuggestionBatch> latestManualRefresh = batchRepository
                .findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                        user.getUserId(),
                        StockAiSuggestionTriggerReason.MANUAL_REFRESH
                );
        RefreshCooldown refreshCooldown = calculateRefreshCooldown(user.getUserId(), latestManualRefresh.orElse(null));

        if (!refreshCooldown.refreshAllowed()) {
            return latestBatch
                    .map(batch -> buildResponse(
                            user,
                            batch,
                            "Please wait until the refresh cooldown ends.",
                            true,
                            false,
                            refreshCooldown.nextRefreshAllowedAt()
                    ))
                    .orElseGet(() -> buildEmptyResponse(
                            user,
                            null,
                            false,
                            refreshCooldown.nextRefreshAllowedAt(),
                            "Please wait until the refresh cooldown ends."
                    ));
        }

        List<StockAnalysisSnapshot> snapshots = loadUsableSnapshots();
        if (snapshots.isEmpty()) {
            return buildEmptyResponse(user, null, "No usable stock analysis data is available for suggestions yet.");
        }

        String model = openAiClient.getModel();
        String inputHash = hashInput(user, profile, snapshots, model);
        Optional<StockAiSuggestionBatch> existingInputBatch = batchRepository
                .findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash
                );

        if (existingInputBatch.isPresent() && existingInputBatch.get().getStatus() != StockAiSuggestionBatchStatus.FAILED) {
            return buildResponse(user, existingInputBatch.get(), "Returned existing suggestions because your profile and stock data are unchanged.", false);
        }

        GenerationResult generationResult = generateWithOpenAi(profile, snapshots);
        if (generationResult.success()) {
            StockAiSuggestionBatch saved = saveBatchAndItems(
                    user,
                    profile,
                    snapshots,
                    inputHash,
                    model,
                    StockAiSuggestionBatchStatus.SUCCESS,
                    StockAiSuggestionTriggerReason.MANUAL_REFRESH,
                    generationResult.content(),
                    generationResult.openAiResult(),
                    null
            );
            return buildResponse(user, saved, "Generated new AI stock suggestions", false);
        }

        Optional<StockAiSuggestionBatch> cached = batchRepository
                .findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        user.getUserId(),
                        StockAiSuggestionBatchStatus.SUCCESS,
                        now.minusDays(3)
                );
        if (cached.isPresent()) {
            return buildResponse(user, cached.get(), "AI suggestions are temporarily unavailable, so the latest cached suggestions are shown.", true);
        }

        AiSuggestionContentDto fallback = buildRuleBasedFallback(profile, snapshots);
        StockAiSuggestionBatch fallbackBatch = saveBatchAndItems(
                user,
                profile,
                snapshots,
                inputHash,
                model,
                StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED,
                StockAiSuggestionTriggerReason.MANUAL_REFRESH,
                fallback,
                null,
                generationResult.errorMessage()
        );
        return buildResponse(user, fallbackBatch, "AI suggestions are temporarily unavailable, so a simple rule-based fallback is shown.", true);
    }

    @Override
    @Transactional
    public StockAiSuggestionResponse dismissSuggestionForCurrentUser(Long itemId) {
        AppUser user = currentUserService.getCurrentUser();
        StockAiSuggestionItem item = itemRepository.findBySuggestionItemIdAndUserUserId(itemId, user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Suggestion item not found"));

        LocalDateTime now = LocalDateTime.now();
        item.setStatus(StockAiSuggestionItemStatus.DISMISSED);
        item.setDismissedAt(now);
        item.setUpdatedAt(now);
        itemRepository.save(item);

        return buildResponse(user, item.getSuggestionBatch(), "Suggestion dismissed", false);
    }

    @Override
    @Transactional
    public StockAiSuggestionResponse watchlistSuggestionForCurrentUser(Long itemId) {
        AppUser user = currentUserService.getCurrentUser();
        StockAiSuggestionItem item = itemRepository.findBySuggestionItemIdAndUserUserId(itemId, user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Suggestion item not found"));

        if (!isWatchlistableSuggestionItem(item)) {
            throw new IllegalArgumentException("Only active or already watchlisted suggestion items can be watchlisted from this endpoint. Use a stock watchlist action for dismissed or expired suggestions.");
        }

        LocalDateTime now = LocalDateTime.now();
        UserWatchlist watchlist = watchlistRepository.findByUserUserIdAndSymbol(user.getUserId(), item.getSymbol())
                .orElseGet(() -> {
                    UserWatchlist row = new UserWatchlist();
                    row.setUser(user);
                    row.setSymbol(item.getSymbol());
                    row.setCreatedAt(now);
                    return row;
                });
        watchlist.setSource(WatchlistSource.AI_SUGGESTION);
        watchlist.setUpdatedAt(now);
        watchlistRepository.save(watchlist);

        item.setStatus(StockAiSuggestionItemStatus.WATCHLISTED);
        item.setUpdatedAt(now);
        itemRepository.save(item);

        return buildResponse(user, item.getSuggestionBatch(), "Suggestion added to watchlist", false);
    }

    private boolean isWatchlistableSuggestionItem(StockAiSuggestionItem item) {
        return item.getStatus() == StockAiSuggestionItemStatus.ACTIVE
                || item.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED;
    }

    private List<StockAnalysisSnapshot> loadUsableSnapshots() {
        List<StockAnalysisSnapshot> snapshots = new ArrayList<>();
        for (String symbol : SUPPORTED_SYMBOLS) {
            try {
                snapshots.add(stockAnalysisService.createOrReuseSnapshot(symbol, ANALYSIS_TIMEFRAME));
            } catch (RuntimeException ignored) {
                // A single unavailable stock should not block suggestions for the other supported symbols.
            }
        }
        return snapshots;
    }

    private GenerationResult generateWithOpenAi(UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots) {
        String userContent = buildPromptInput(profile, snapshots, null);
        OpenAiSuggestionResult firstResult = openAiClient.generateSuggestion(SYSTEM_PROMPT, userContent);
        GenerationResult firstParsed = parseAndValidate(firstResult, profile, snapshots);
        if (firstParsed.success()) {
            return firstParsed;
        }

        String retryContent = buildPromptInput(profile, snapshots, firstParsed.errorMessage());
        OpenAiSuggestionResult retryResult = openAiClient.generateSuggestion(SYSTEM_PROMPT, retryContent);
        return parseAndValidate(retryResult, profile, snapshots);
    }

    private GenerationResult parseAndValidate(OpenAiSuggestionResult result, UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots) {
        if (!result.success()) {
            return GenerationResult.failure(result.errorMessage());
        }

        try {
            AiSuggestionContentDto content = objectMapper.readValue(cleanJson(result.content()), AiSuggestionContentDto.class);
            validateAiContent(content, profile, snapshots);
            return GenerationResult.success(content, result);
        } catch (Exception e) {
            return GenerationResult.failure(e.getMessage());
        }
    }

    private String cleanJson(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return value;
    }

    private void validateAiContent(AiSuggestionContentDto content, UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots) {
        if (content == null || content.suggestedStocks() == null || content.suggestedStocks().isEmpty()) {
            throw new IllegalArgumentException("AI returned no suggested stocks");
        }
        if (content.suggestedStocks().size() > Math.min(MAX_SUGGESTIONS, snapshots.size())) {
            throw new IllegalArgumentException("AI returned too many suggested stocks");
        }

        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
                .collect(Collectors.toMap(StockAnalysisSnapshot::getSymbol, Function.identity()));
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < content.suggestedStocks().size(); i++) {
            AiSuggestedStockDto stock = content.suggestedStocks().get(i);
            String symbol = normalizeSymbol(stock.symbol());
            if (!SUPPORTED_SYMBOLS.contains(symbol) || !snapshotBySymbol.containsKey(symbol)) {
                throw new IllegalArgumentException("AI returned unsupported symbol: " + stock.symbol());
            }
            if (!seen.add(symbol)) {
                throw new IllegalArgumentException("AI returned duplicate symbol: " + symbol);
            }
            if (!Integer.valueOf(i + 1).equals(stock.rankNo())) {
                throw new IllegalArgumentException("AI returned non-sequential rank");
            }
            if (stock.matchScore() == null || stock.matchScore() < 0 || stock.matchScore() > 100) {
                throw new IllegalArgumentException("AI returned invalid match score");
            }

            StockAnalysisSnapshot snapshot = snapshotBySymbol.get(symbol);
            if (!equalsIgnoreCase(snapshot.getRiskCategory(), stock.riskLevel())) {
                throw new IllegalArgumentException("AI risk level does not match snapshot risk category");
            }
            if (isBlank(stock.shortReason()) || isBlank(stock.detailReason())) {
                throw new IllegalArgumentException("AI returned blank suggestion reason");
            }
            validateReasonText(stock.shortReason(), stock.detailReason());
            validateDataQualityScore(stock, snapshot, snapshots);
        }

        validateRiskReasonableness(content, profile, snapshots);
    }

    private void validateReasonText(String shortReason, String detailReason) {
        String combined = (shortReason + " " + detailReason).toLowerCase(Locale.ROOT);
        List<String> banned = List.of(
                "you should buy", "you should sell", "should buy", "should sell",
                "guaranteed", "guarantee", "will rise", "will increase", "will go up",
                "will fall", "will recover", "will outperform", "expected return",
                "future price", "news", "earnings", "sector"
        );
        if (banned.stream().anyMatch(combined::contains)) {
            throw new IllegalArgumentException("AI returned advice, prediction, or unsupported external reason");
        }

        String detail = detailReason.toLowerCase(Locale.ROOT);
        int factorCount = 0;
        for (String factor : List.of("trend", "volatility", "volume", "consistency", "risk", "fallback", "missing", "data")) {
            if (detail.contains(factor)) {
                factorCount++;
            }
        }
        if (factorCount < 2) {
            throw new IllegalArgumentException("AI detail reason does not mention enough input factors");
        }
    }

    private void validateDataQualityScore(AiSuggestedStockDto stock, StockAnalysisSnapshot snapshot, List<StockAnalysisSnapshot> snapshots) {
        boolean enoughCompleteAlternatives = snapshots.stream()
                .filter(candidate -> !candidate.getSymbol().equals(snapshot.getSymbol()))
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsFallback()))
                .filter(candidate -> candidate.getMissingDataCount() == null || candidate.getMissingDataCount() == 0)
                .count() >= 2;
        int missingDataCount = snapshot.getMissingDataCount() == null ? 0 : snapshot.getMissingDataCount();
        if ((Boolean.TRUE.equals(snapshot.getIsFallback()) || missingDataCount >= 3)
                && stock.matchScore() >= 80
                && enoughCompleteAlternatives) {
            throw new IllegalArgumentException("AI gave a high score to incomplete data while alternatives exist");
        }
    }

    private void validateRiskReasonableness(AiSuggestionContentDto content, UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots) {
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
                .collect(Collectors.toMap(StockAnalysisSnapshot::getSymbol, Function.identity()));
        boolean saferAlternativesExist = snapshots.stream()
                .map(StockAnalysisSnapshot::getRiskCategory)
                .anyMatch(risk -> equalsIgnoreCase(risk, "conservative") || equalsIgnoreCase(risk, "moderate"));
        boolean growthAlternativesExist = snapshots.stream()
                .map(StockAnalysisSnapshot::getRiskCategory)
                .anyMatch(risk -> equalsIgnoreCase(risk, "aggressive") || equalsIgnoreCase(risk, "moderate"));
        boolean highBehaviorConfidence = profile.getBehaviorConfidence() == BehaviorConfidence.HIGH;

        AiSuggestedStockDto top = content.suggestedStocks().get(0);
        StockAnalysisSnapshot topSnapshot = snapshotBySymbol.get(normalizeSymbol(top.symbol()));
        if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                && equalsIgnoreCase(topSnapshot.getRiskCategory(), "aggressive")
                && saferAlternativesExist) {
            throw new IllegalArgumentException("Conservative profile received aggressive top suggestion");
        }

        for (AiSuggestedStockDto stock : content.suggestedStocks()) {
            StockAnalysisSnapshot snapshot = snapshotBySymbol.get(normalizeSymbol(stock.symbol()));
            if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                    && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")
                    && stock.matchScore() >= 85
                    && !highBehaviorConfidence) {
                throw new IllegalArgumentException("Conservative profile received overly high aggressive score");
            }
            if (profile.getRiskTolerance() == RiskTolerance.MODERATE
                    && profile.getExperienceLevel() != null
                    && profile.getExperienceLevel().name().equals("BEGINNER")
                    && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")
                    && stock.matchScore() >= 80
                    && !highBehaviorConfidence) {
                throw new IllegalArgumentException("Moderate beginner received overly high aggressive score");
            }
        }

        boolean allConservative = content.suggestedStocks().stream()
                .map(stock -> snapshotBySymbol.get(normalizeSymbol(stock.symbol())))
                .allMatch(snapshot -> equalsIgnoreCase(snapshot.getRiskCategory(), "conservative"));
        if (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE && allConservative && growthAlternativesExist) {
            throw new IllegalArgumentException("Aggressive profile received only conservative suggestions");
        }
    }

    private AiSuggestionContentDto buildRuleBasedFallback(UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots) {
        List<AiSuggestedStockDto> suggestions = snapshots.stream()
                .map(snapshot -> new RankedSnapshot(snapshot, ruleBasedScore(profile, snapshot)))
                .sorted(Comparator.comparing(RankedSnapshot::score).reversed())
                .limit(Math.min(MAX_SUGGESTIONS, snapshots.size()))
                .map(ranked -> toFallbackSuggestion(ranked.snapshot(), ranked.score()))
                .toList();

        List<AiSuggestedStockDto> rankedSuggestions = new ArrayList<>();
        for (int i = 0; i < suggestions.size(); i++) {
            AiSuggestedStockDto suggestion = suggestions.get(i);
            rankedSuggestions.add(new AiSuggestedStockDto(
                    suggestion.symbol(),
                    i + 1,
                    suggestion.matchScore(),
                    suggestion.riskLevel(),
                    suggestion.suggestionLabel(),
                    suggestion.shortReason(),
                    suggestion.detailReason()
            ));
        }

        return new AiSuggestionContentDto(
                "These suggestions are ranked by your onboarding risk profile and current data completeness.",
                rankedSuggestions
        );
    }

    private int ruleBasedScore(UserInvestmentProfile profile, StockAnalysisSnapshot snapshot) {
        String risk = snapshot.getRiskCategory() == null ? "moderate" : snapshot.getRiskCategory().toLowerCase(Locale.ROOT);
        int score = switch (profile.getRiskTolerance()) {
            case CONSERVATIVE -> switch (risk) {
                case "conservative" -> 92;
                case "moderate" -> 78;
                default -> 45;
            };
            case AGGRESSIVE -> switch (risk) {
                case "aggressive" -> 90;
                case "moderate" -> 75;
                default -> 55;
            };
            default -> switch (risk) {
                case "moderate" -> 88;
                case "conservative" -> 76;
                default -> 65;
            };
        };

        if (profile.getPreferredVolatility() == PreferredVolatility.LOW && "high".equalsIgnoreCase(snapshot.getVolatilityLabel())) {
            score -= 15;
        }
        if (profile.getPreferredVolatility() == PreferredVolatility.HIGH && "high".equalsIgnoreCase(snapshot.getVolatilityLabel())) {
            score += 5;
        }
        if (Boolean.TRUE.equals(snapshot.getIsFallback())) {
            score -= 12;
        }
        score -= Math.min(20, (snapshot.getMissingDataCount() == null ? 0 : snapshot.getMissingDataCount()) * 3);
        return Math.max(0, Math.min(100, score));
    }

    private AiSuggestedStockDto toFallbackSuggestion(StockAnalysisSnapshot snapshot, int score) {
        String companyName = StockMetadata.COMPANY_MAP.getOrDefault(snapshot.getSymbol(), snapshot.getSymbol());
        String risk = snapshot.getRiskCategory() == null ? "moderate" : snapshot.getRiskCategory();
        return new AiSuggestedStockDto(
                snapshot.getSymbol(),
                1,
                score,
                risk,
                "Profile-aligned learning pick",
                companyName + " matches your profile using risk and data completeness.",
                companyName + " has a " + risk + " risk category with " + snapshot.getVolatilityLabel()
                        + " volatility and " + snapshot.getPriceConsistency()
                        + ". Its data quality is considered through fallback status and missing data count."
        );
    }

    private StockAiSuggestionBatch saveBatchAndItems(
            AppUser user,
            UserInvestmentProfile profile,
            List<StockAnalysisSnapshot> snapshots,
            String inputHash,
            String model,
            StockAiSuggestionBatchStatus status,
            StockAiSuggestionTriggerReason triggerReason,
            AiSuggestionContentDto content,
            OpenAiSuggestionResult openAiResult,
            String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        expirePreviousActiveItems(user.getUserId(), now);

        StockAiSuggestionBatch batch = new StockAiSuggestionBatch();
        batch.setUser(user);
        batch.setProfile(profile);
        batch.setProfileVersion(profile.getProfileVersion());
        batch.setModel(model);
        batch.setPromptVersion(PROMPT_VERSION);
        batch.setStatus(status);
        batch.setTriggerReason(triggerReason);
        batch.setInputHash(inputHash);
        batch.setBatchSummary(content.batchSummary());
        batch.setAnalysisTimeframe(ANALYSIS_TIMEFRAME);
        batch.setPromptTokens(openAiResult == null ? null : openAiResult.promptTokens());
        batch.setCompletionTokens(openAiResult == null ? null : openAiResult.completionTokens());
        batch.setTotalTokens(openAiResult == null ? null : openAiResult.totalTokens());
        batch.setFinishReason(openAiResult == null ? null : openAiResult.finishReason());
        batch.setErrorMessage(errorMessage);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batch.setExpiresAt(now.plusHours(BATCH_EXPIRY_HOURS));
        StockAiSuggestionBatch savedBatch = batchRepository.save(batch);

        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
                .collect(Collectors.toMap(StockAnalysisSnapshot::getSymbol, Function.identity()));
        for (AiSuggestedStockDto suggestedStock : content.suggestedStocks()) {
            String symbol = normalizeSymbol(suggestedStock.symbol());
            StockAnalysisSnapshot snapshot = snapshotBySymbol.get(symbol);
            if (snapshot == null) {
                continue;
            }
            StockAiSuggestionItem item = new StockAiSuggestionItem();
            item.setSuggestionBatch(savedBatch);
            item.setUser(user);
            item.setSymbol(symbol);
            item.setRankNo(suggestedStock.rankNo());
            item.setMatchScore(suggestedStock.matchScore());
            item.setRiskLevel(suggestedStock.riskLevel());
            item.setSuggestionLabel(suggestedStock.suggestionLabel());
            item.setShortReason(suggestedStock.shortReason());
            item.setDetailReason(suggestedStock.detailReason());
            item.setAnalysisSnapshot(snapshot);
            item.setStatus(StockAiSuggestionItemStatus.ACTIVE);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            itemRepository.save(item);
        }
        return savedBatch;
    }

    private void expirePreviousActiveItems(Long userId, LocalDateTime now) {
        List<StockAiSuggestionItem> activeItems = itemRepository.findByUserUserIdAndStatus(
                userId,
                StockAiSuggestionItemStatus.ACTIVE
        );
        for (StockAiSuggestionItem item : activeItems) {
            item.setStatus(StockAiSuggestionItemStatus.EXPIRED);
            item.setUpdatedAt(now);
            itemRepository.save(item);
        }
    }

    private StockAiSuggestionResponse buildResponse(AppUser user, StockAiSuggestionBatch batch, String message, boolean fallbackUsed) {
        RefreshCooldown refreshCooldown = calculateRefreshCooldown(user.getUserId(), batch);
        return buildResponse(
                user,
                batch,
                message,
                fallbackUsed,
                refreshCooldown.refreshAllowed(),
                refreshCooldown.nextRefreshAllowedAt()
        );
    }

    private StockAiSuggestionResponse buildResponse(
            AppUser user,
            StockAiSuggestionBatch batch,
            String message,
            boolean fallbackUsed,
            boolean refreshAllowed,
            LocalDateTime nextRefreshAllowedAt
    ) {
        List<StockAiSuggestionItem> topItems = itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(batch, TOP_ITEM_STATUSES);
        Set<String> topSymbols = topItems.stream().map(StockAiSuggestionItem::getSymbol).collect(Collectors.toSet());
        List<String> symbols = new ArrayList<>(SUPPORTED_SYMBOLS);
        Map<String, Stock> stockBySymbol = stockRepository.findBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
        Set<String> watchlistedSymbols = watchlistRepository.findByUserUserIdAndSymbolIn(user.getUserId(), symbols).stream()
                .map(UserWatchlist::getSymbol)
                .collect(Collectors.toSet());

        return new StockAiSuggestionResponse(
                user.getUserId(),
                batch.getSuggestionBatchId(),
                batch.getStatus().name(),
                batch.getTriggerReason().name(),
                batch.getBatchSummary(),
                batch.getAnalysisTimeframe(),
                batch.getCreatedAt(),
                batch.getExpiresAt(),
                fallbackUsed || batch.getStatus() != StockAiSuggestionBatchStatus.SUCCESS,
                refreshAllowed,
                nextRefreshAllowedAt,
                topItems.stream()
                        .map(item -> toSuggestedResponse(item, stockBySymbol.get(item.getSymbol()), watchlistedSymbols.contains(item.getSymbol())))
                        .toList(),
                buildRemainingStocks(topSymbols, stockBySymbol, watchlistedSymbols),
                message
        );
    }

    private StockAiSuggestionResponse buildEmptyResponse(
            AppUser user,
            StockAiSuggestionBatch batch,
            String message
    ) {
        RefreshCooldown refreshCooldown = calculateRefreshCooldown(user.getUserId(), batch);
        return buildEmptyResponse(
                user,
                batch,
                refreshCooldown.refreshAllowed(),
                refreshCooldown.nextRefreshAllowedAt(),
                message
        );
    }

    private StockAiSuggestionResponse buildEmptyResponse(
            AppUser user,
            StockAiSuggestionBatch batch,
            boolean refreshAllowed,
            LocalDateTime nextRefreshAllowedAt,
            String message
    ) {
        List<String> symbols = new ArrayList<>(SUPPORTED_SYMBOLS);
        Map<String, Stock> stockBySymbol = stockRepository.findBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (first, second) -> first));
        Set<String> watchlistedSymbols = watchlistRepository.findByUserUserIdAndSymbolIn(user.getUserId(), symbols).stream()
                .map(UserWatchlist::getSymbol)
                .collect(Collectors.toSet());

        return new StockAiSuggestionResponse(
                user.getUserId(),
                batch == null ? null : batch.getSuggestionBatchId(),
                batch == null ? null : batch.getStatus().name(),
                batch == null ? null : batch.getTriggerReason().name(),
                batch == null ? null : batch.getBatchSummary(),
                ANALYSIS_TIMEFRAME,
                batch == null ? null : batch.getCreatedAt(),
                batch == null ? null : batch.getExpiresAt(),
                false,
                refreshAllowed,
                nextRefreshAllowedAt,
                List.of(),
                buildRemainingStocks(Set.of(), stockBySymbol, watchlistedSymbols),
                message
        );
    }

    private RefreshCooldown calculateRefreshCooldown(Long userId, StockAiSuggestionBatch currentBatch) {
        LocalDateTime now = LocalDateTime.now();
        StockAiSuggestionBatch latestManualBatch = batchRepository
                .findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                        userId,
                        StockAiSuggestionTriggerReason.MANUAL_REFRESH
                )
                .orElse(null);

        if (currentBatch != null
                && currentBatch.getTriggerReason() == StockAiSuggestionTriggerReason.MANUAL_REFRESH
                && currentBatch.getCreatedAt() != null
                && (latestManualBatch == null
                || latestManualBatch.getCreatedAt() == null
                || currentBatch.getCreatedAt().isAfter(latestManualBatch.getCreatedAt()))) {
            latestManualBatch = currentBatch;
        }

        if (latestManualBatch == null || latestManualBatch.getCreatedAt() == null) {
            return new RefreshCooldown(true, null);
        }

        LocalDateTime nextRefreshAllowedAt = latestManualBatch.getCreatedAt().plusHours(MANUAL_REFRESH_COOLDOWN_HOURS);
        if (nextRefreshAllowedAt.isAfter(now)) {
            return new RefreshCooldown(false, nextRefreshAllowedAt);
        }
        return new RefreshCooldown(true, null);
    }

    private SuggestedStockResponse toSuggestedResponse(StockAiSuggestionItem item, Stock stock, boolean isWatchlisted) {
        StockAnalysisSnapshot snapshot = item.getAnalysisSnapshot();
        return new SuggestedStockResponse(
                item.getSuggestionItemId(),
                stock == null ? null : stock.getStockId(),
                item.getSymbol(),
                stock == null ? StockMetadata.COMPANY_MAP.getOrDefault(item.getSymbol(), item.getSymbol()) : stock.getCompanyName(),
                item.getRankNo(),
                item.getMatchScore(),
                item.getRiskLevel(),
                item.getSuggestionLabel(),
                item.getShortReason(),
                item.getDetailReason(),
                item.getStatus().name(),
                snapshot == null ? null : snapshot.getAnalysisSnapshotId(),
                snapshot == null ? (stock == null ? null : stock.getCurrentPrice()) : snapshot.getCurrentPrice(),
                snapshot == null ? (stock == null ? null : stock.getPercentChange()) : snapshot.getPercentChange(),
                snapshot == null ? null : snapshot.getTrend(),
                snapshot == null ? null : snapshot.getVolatilityLabel(),
                snapshot == null ? null : snapshot.getVolumeTrend(),
                snapshot == null ? null : snapshot.getPriceConsistency(),
                snapshot == null ? null : snapshot.getIsFallback(),
                snapshot == null ? null : snapshot.getMissingDataCount(),
                isWatchlisted || item.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED
        );
    }

    private List<RemainingStockResponse> buildRemainingStocks(
            Set<String> excludedSymbols,
            Map<String, Stock> stockBySymbol,
            Set<String> watchlistedSymbols
    ) {
        return SUPPORTED_SYMBOLS.stream()
                .filter(symbol -> !excludedSymbols.contains(symbol))
                .map(symbol -> {
                    Stock stock = stockBySymbol.get(symbol);
                    StockAnalysisSnapshot snapshot = snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDesc(symbol, ANALYSIS_TIMEFRAME).orElse(null);
                    return new RemainingStockResponse(
                            stock == null ? null : stock.getStockId(),
                            symbol,
                            stock == null ? StockMetadata.COMPANY_MAP.getOrDefault(symbol, symbol) : stock.getCompanyName(),
                            snapshot == null ? (stock == null ? null : stock.getCurrentPrice()) : snapshot.getCurrentPrice(),
                            snapshot == null ? (stock == null ? null : stock.getPercentChange()) : snapshot.getPercentChange(),
                            snapshot == null ? null : snapshot.getTrend(),
                            snapshot == null ? null : snapshot.getVolatilityLabel(),
                            snapshot == null ? StockMetadata.RISK_CATEGORY_MAP.getOrDefault(symbol, "moderate") : snapshot.getRiskCategory(),
                            false,
                            watchlistedSymbols.contains(symbol)
                    );
                })
                .toList();
    }

    private String buildPromptInput(UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots, String validationError) {
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> userProfile = new LinkedHashMap<>();
        userProfile.put("riskTolerance", profile.getRiskTolerance());
        userProfile.put("investmentGoal", profile.getInvestmentGoal());
        userProfile.put("experienceLevel", profile.getExperienceLevel());
        userProfile.put("preferredVolatility", profile.getPreferredVolatility());
        userProfile.put("preferredHorizon", profile.getPreferredHorizon());
        userProfile.put("riskScore", valueOrBlank(profile.getRiskScore()));
        userProfile.put("goalScore", valueOrBlank(profile.getGoalScore()));
        userProfile.put("experienceScore", valueOrBlank(profile.getExperienceScore()));
        userProfile.put("behaviorRiskScore", valueOrBlank(profile.getBehaviorRiskScore()));
        userProfile.put("behaviorStyle", valueOrBlank(profile.getBehaviorStyle()));
        userProfile.put("behaviorConfidence", profile.getBehaviorConfidence() == null ? BehaviorConfidence.LOW : profile.getBehaviorConfidence());
        input.put("userProfile", userProfile);
        input.put("analysisTimeframe", ANALYSIS_TIMEFRAME);
        input.put("maxSuggestions", Math.min(MAX_SUGGESTIONS, snapshots.size()));
        input.put("supportedSymbols", SUPPORTED_SYMBOLS);
        input.put("stockSnapshots", snapshots.stream().map(snapshot -> Map.ofEntries(
                Map.entry("symbol", snapshot.getSymbol()),
                Map.entry("companyName", StockMetadata.COMPANY_MAP.getOrDefault(snapshot.getSymbol(), snapshot.getSymbol())),
                Map.entry("currentPrice", valueOrBlank(snapshot.getCurrentPrice())),
                Map.entry("percentChange", valueOrBlank(snapshot.getPercentChange())),
                Map.entry("trend", valueOrBlank(snapshot.getTrend())),
                Map.entry("volatilityLabel", valueOrBlank(snapshot.getVolatilityLabel())),
                Map.entry("volumeTrend", valueOrBlank(snapshot.getVolumeTrend())),
                Map.entry("priceConsistency", valueOrBlank(snapshot.getPriceConsistency())),
                Map.entry("highPrice", valueOrBlank(snapshot.getHighPrice())),
                Map.entry("lowPrice", valueOrBlank(snapshot.getLowPrice())),
                Map.entry("baselineRiskCategory", valueOrBlank(snapshot.getBaselineRiskCategory())),
                Map.entry("riskCategory", valueOrBlank(snapshot.getRiskCategory())),
                Map.entry("dataSource", valueOrBlank(snapshot.getDataSource())),
                Map.entry("isFallback", Boolean.TRUE.equals(snapshot.getIsFallback())),
                Map.entry("missingDataCount", snapshot.getMissingDataCount() == null ? 0 : snapshot.getMissingDataCount()),
                Map.entry("snapshotId", valueOrBlank(snapshot.getAnalysisSnapshotId())),
                Map.entry("snapshotHash", valueOrBlank(snapshot.getSnapshotHash()))
        )).toList());
        if (!isBlank(validationError)) {
            input.put("previousValidationError", validationError);
            input.put("retryInstruction", "Fix the validation issue and return only valid JSON.");
        }

        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build suggestion prompt input", e);
        }
    }

    private Object valueOrBlank(Object value) {
        return value == null ? "" : value;
    }

    private String hashInput(AppUser user, UserInvestmentProfile profile, List<StockAnalysisSnapshot> snapshots, String model) {
        String snapshotHashPart = snapshots.stream()
                .sorted(Comparator.comparing(StockAnalysisSnapshot::getSymbol))
                .map(snapshot -> snapshot.getSymbol() + ":" + snapshot.getSnapshotHash())
                .collect(Collectors.joining("|"));
        String raw = String.join("|",
                String.valueOf(user.getUserId()),
                String.valueOf(profile.getProfileId()),
                String.valueOf(profile.getProfileVersion()),
                String.valueOf(profile.getBehaviorRiskScore()),
                String.valueOf(profile.getBehaviorStyle()),
                String.valueOf(profile.getBehaviorConfidence()),
                snapshotHashPart,
                model,
                PROMPT_VERSION,
                ANALYSIS_TIMEFRAME
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record GenerationResult(
            boolean success,
            AiSuggestionContentDto content,
            OpenAiSuggestionResult openAiResult,
            String errorMessage
    ) {
        static GenerationResult success(AiSuggestionContentDto content, OpenAiSuggestionResult openAiResult) {
            return new GenerationResult(true, content, openAiResult, null);
        }

        static GenerationResult failure(String errorMessage) {
            return new GenerationResult(false, null, null, errorMessage);
        }
    }

    private record RankedSnapshot(StockAnalysisSnapshot snapshot, int score) {
    }

    private record RefreshCooldown(boolean refreshAllowed, LocalDateTime nextRefreshAllowedAt) {
    }
}
