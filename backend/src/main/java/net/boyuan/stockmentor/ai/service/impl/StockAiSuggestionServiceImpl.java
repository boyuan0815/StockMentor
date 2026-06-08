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
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(StockAiSuggestionServiceImpl.class);

    private final CurrentUserService currentUserService;
    private final UserInvestmentProfileRepository profileRepository;
    private final StockAiSuggestionBatchRepository batchRepository;
    private final StockAiSuggestionItemRepository itemRepository;
    private final UserWatchlistRepository watchlistRepository;
    private final StockRepository stockRepository;
    private final StockAnalysisSnapshotRepository snapshotRepository;
    private final StockAnalysisService stockAnalysisService;
    private final OpenAiClient openAiClient;
    private final UserBehaviorProfileService behaviorProfileService;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_VERSION = "stock-suggestion-v2";
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
    private static final List<StockAiSuggestionBatchStatus> REUSABLE_INPUT_STATUSES = List.of(
            StockAiSuggestionBatchStatus.SUCCESS,
            StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
    );
    private static final List<StockAiSuggestionItemStatus> TOP_ITEM_STATUSES = List.of(
            StockAiSuggestionItemStatus.ACTIVE,
            StockAiSuggestionItemStatus.WATCHLISTED
    );
    private static final String SYSTEM_PROMPT = """
            You are an educational stock suggestion assistant for StockMentor, a beginner paper-trading application.
            
            Your task is to rank suitable stocks for educational paper-trading practice based only on the structured data provided by the backend.
            
            You will receive:
            
            * one beginner investor profile
            * one virtual trading behavior summary
            * structured analysis snapshots for exactly 8 supported stocks
            
            Important meaning:
            
            * The suggestions are for paper-trading education only.
            * The matchScore is an educational suitability score, not an expected return score.
            * The output must not be interpreted as real financial advice.
            
            Strict data rules:
            
            * Use only the provided structured data.
            * Do not use external news, market knowledge, company reputation, real-world events, sector assumptions, or information not included in the input.
            * Do not describe a company by industry or sector unless the input explicitly provides that information.
            * Do not predict future prices.
            * Do not claim that any stock will rise, recover, outperform, or generate gains.
            * Do not give real investment advice.
            The AI suggestion explanation must not recommend actions.
            Avoid phrases such as:
            * "you should buy", "you should sell", "hold this stock", "guaranteed profit", "expected return", "will recover", "will outperform"
            The words "buy" and "sell" may exist in system UI labels, but they must not appear as AI advice in generated suggestion reasons.
            * Do not suggest any stock outside the provided stock list.
            * Do not invent missing fields.
            
            Ranking rules:
            
            * Return maximum 3 suggested stocks.
            * Rank stocks by suitability for the given user profile and behavior summary.
            * Prefer stocks whose final risk category and volatility fit the user profile.
            * Use the behavior summary as a secondary adjustment, not as the only decision factor.
            * If behaviorConfidence is LOW or MEDIUM, do not let behavior summary override the user's main risk tolerance, experience level, and preferred volatility.
            * If behaviorConfidence is HIGH, behavior summary may have stronger influence, but the explanation must clearly state why.
            * When two stocks have similar risk and volatility fit, rank the stock with clearer trend and smoother price consistency higher.
            * For growth-oriented users, an uptrend or steady uptrend is preferred over sideways movement when risk and volatility are still suitable.
            * If the user has moderate risk tolerance, avoid ranking highly aggressive stocks too high unless behavior data strongly supports it.
            * If experienceLevel is BEGINNER and riskTolerance is MODERATE, do not suggest stocks with final risk category above moderate unless behaviorConfidence is HIGH or there are fewer than 3 suitable moderate or conservative stocks.
            * If the user has conservative risk tolerance, prefer lower volatility and conservative or moderate risk categories.
            * If the user has aggressive risk tolerance, higher volatility stocks can be considered, but explanations must remain educational and cautious.
            * If a stock has fallback data or high missing data count, reduce its suitability.
            * If fewer than 3 suitable stocks exist, return fewer than 3 and explain this in batchSummary.
            * batchSummary must say these are the "top suggested stocks" or "selected suggestions"; do not say "only three stocks fit" unless fewer than 3 valid stocks truly exist.
            * Data quality must affect ranking strongly. If two stocks both match the user profile, prefer the stock with Fallback = false and lower Missing data count.
            * A stock with Fallback = true or Missing data count greater than 0 may still be suggested, but it should normally rank below a similar-fit stock with complete data.
            * Do not rely only on risk category when ranking. If several stocks share the same risk category, compare their movement quality and data quality.
            * A stock with highly erratic price consistency should normally rank below a similar stock with smoother price consistency for beginner users.
            * A stock with clearer movement and complete data should normally rank above a similar stock with unclear or erratic movement.
            * Do not describe a stock as steady, smooth, or consistent unless the provided trend or priceConsistency clearly supports that wording.
            * If priceConsistency is "choppy", "choppy upward movement", "choppy downward movement", or "highly erratic movement", describe it honestly as choppy or erratic.
            * If trend contains "volatile", do not describe the trend as steady.
            * A stock with "highly erratic movement" should normally rank below a similar stock with smoother movement and should usually receive a lower matchScore.
            
            matchScore rules:
            
            * matchScore must be an integer from 0 to 100.
            * matchScore means educational suitability for paper-trading practice, not expected return, profit potential, or future performance.
            * Avoid giving the same matchScore to multiple selected stocks unless their provided data is truly almost identical.
            * Rank 1 should normally have the highest matchScore.
            * Rank 2 should normally have the same or lower matchScore than rank 1.
            * Rank 3 should normally have the same or lower matchScore than rank 2.
            * Small score differences are enough. Use differences such as 2 to 5 points when stocks are close.
            * Do not create artificial large score gaps when stocks are similar.
            * 90 to 100 means very strong educational fit.
            * 80 to 89 means strong educational fit.
            * 70 to 79 means acceptable educational fit.
            * 60 to 69 means weak but still usable educational fit.
            * Below 60 should normally not be suggested unless there are not enough suitable stocks.
            * Do not give extremely high scores too easily.
            * For this educational paper-trading use case, avoid scores above 90 unless the stock strongly matches risk tolerance, preferred volatility, experience level, trend clarity, price consistency, behavior summary, and data quality.
            * For beginner users, a strong match should usually be in the 80 to 89 range.
            * If the stock has a risk category above the user's risk tolerance, matchScore should normally be below 80.
            * If the stock's volatility does not match preferredVolatility, reduce matchScore unless other factors strongly compensate.
            * If priceConsistency is highly erratic, reduce matchScore compared with a similar stock that has smoother or clearer movement.
            * If trend is sideways or unclear, reduce matchScore compared with a similar stock that has clearer movement.
            * If volumeTrend is unusually high, increasing, or unstable, mention it carefully and do not treat it as automatically positive.
            * If Fallback is true, reduce matchScore.
            * If Missing data count is greater than 0, reduce matchScore based on severity.
            * If Fallback is true and Missing data count is 3 or higher, matchScore should normally be below 80 unless there are fewer than 3 suitable alternatives.
            * The score must be explainable using risk category, volatility, trend, price consistency, volume trend, behavior summary, and data quality.
            * candidateFitSignals provide non-numeric compatibility context only. They do not provide the final score.
            * Do not infer matchScore from any backend-calculated numeric score.
            * If several stocks have similar risk compatibility, differentiate their matchScore using trend clarity, price consistency, volatility fit, volume trend, and data quality.
            * Do not give identical matchScore values unless the selected stocks are truly almost identical across the provided stock snapshot fields.
            * If priceConsistency is highly erratic, matchScore should normally be below 80 unless the user profile or behavior summary strongly justifies a higher score.
            * If trend is sideways and priceConsistency is choppy downward, avoid strong-match scores above 84.
            * Do not assign a strong score only because riskCategory and volatility fit; movement clarity and price consistency must also support the score.
            
            Reason writing rules:
            
            * shortReason must be one sentence only.
            * shortReason must be simple and beginner-friendly.
            * Avoid abbreviations such as "vol", "mid-vol", or "tech" unless they appear in the input.
            * suggestionLabel must describe the educational reason why the stock is suggested.
            * suggestionLabel must not be the company name, stock symbol, or duplicated shortReason.
            * Good suggestionLabel examples: "Balanced growth practice", "Smooth trend learning", "Stable medium-risk practice", "Controlled higher-volatility learning" and others.
            * detailReason should normally mention at least three provided factors when available, such as risk category, volatility, trend, volume trend, price consistency, and data quality.
            * Avoid phrases like "growth-oriented trading", "stable learning environment", and "good choice".
            * Prefer "paper-trading practice", "educational example", "observing movement", and "matches the provided profile".
            * Do not make the reason sound like real trading advice.
            * Use wording such as "paper-trading practice", "learning example", "easier to observe", "matches the provided profile", and "educational fit".
            * Avoid trading-strategy terms such as "range trading", "trend-following", or "breakout" unless they are provided in the input.
            * Use simpler wording such as "sideways movement practice", "observing steady movement", or "learning from clearer price movement".
            * The reason must not contradict the provided trend, volatilityLabel, volumeTrend, or priceConsistency.
            * Do not say "consistent", "smooth", or "steady" when the provided priceConsistency is choppy or highly erratic.
            * If the stock has choppy or erratic movement, clearly mention that as a learning factor.
            * detailReason must be 40 to 70 words in the final JSON; do not write short 15 to 25 word reasons.
            
            Output format rules:
            
            * Return raw JSON only.
            * Do not wrap the JSON in markdown.
            * Do not include ```json.
            * Do not include explanations outside the JSON.
            * Do not include comments.
            * Do not include extra fields not defined in the schema.
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
                  "detailReason": "Beginner-friendly 40 to 70 word explanation using at least three provided factors."
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
        return generateSuggestionsForUser(user, StockAiSuggestionTriggerReason.MANUAL_REFRESH, true);
    }

    @Override
    @Transactional
    public StockAiSuggestionResponse generateSuggestionsForUser(
            AppUser user,
            StockAiSuggestionTriggerReason triggerReason,
            boolean enforceManualCooldown
    ) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserInvestmentProfile> profileOptional = profileRepository
                .findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId());

        if (profileOptional.isEmpty()) {
            log.info("AI suggestion generation skipped for userId={} triggerReason={} because no investment profile exists",
                    user.getUserId(), triggerReason);
            return buildEmptyResponse(user, null, "Please complete onboarding before requesting AI stock suggestions.");
        }

        UserInvestmentProfile profile = profileOptional.get();
        Optional<StockAiSuggestionBatch> latestBatch = batchRepository
                .findTopByUserUserIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getUserId(),
                        READABLE_BATCH_STATUSES,
                        now
                );
        if (enforceManualCooldown) {
            Optional<StockAiSuggestionBatch> latestManualRefresh = batchRepository
                    .findTopByUserUserIdAndTriggerReasonOrderByCreatedAtDesc(
                            user.getUserId(),
                            StockAiSuggestionTriggerReason.MANUAL_REFRESH
                    );
            RefreshCooldown refreshCooldown = calculateRefreshCooldown(user.getUserId(), latestManualRefresh.orElse(null));

            if (!refreshCooldown.refreshAllowed()) {
                log.info("AI suggestion refresh blocked by cooldown for userId={} nextRefreshAllowedAt={}",
                        user.getUserId(), refreshCooldown.nextRefreshAllowedAt());
                return latestBatch
                        .map(batch -> buildResponse(
                                user,
                                batch,
                                "Please wait until the refresh cooldown ends.",
                                false,
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
        }

        List<StockAnalysisSnapshot> snapshots = loadUsableSnapshots();
        if (snapshots.isEmpty()) {
            log.info("AI suggestion generation skipped for userId={} triggerReason={} because no usable snapshots exist",
                    user.getUserId(), triggerReason);
            return buildEmptyResponse(user, null, "No usable stock analysis data is available for suggestions yet.");
        }

        behaviorProfileService.createLowConfidenceProfileIfMissing(user);
        BehaviorSummaryForSuggestion behaviorSummary = behaviorProfileService.getBehaviorSummaryForSuggestion(user.getUserId());
        List<CandidateFitSignal> candidateFitSignals = buildCandidateFitSignals(profile, behaviorSummary, snapshots);
        log.info("AI suggestion generation promptVersion={} behaviorConfidence={} behaviorRiskScore={} behaviorProfileId={} candidateFitSignals={} userId={} triggerReason={}",
                PROMPT_VERSION,
                behaviorSummary.behaviorConfidence(),
                behaviorSummary.behaviorRiskScore(),
                behaviorSummary.behaviorProfileId(),
                candidateFitSignals(candidateFitSignals),
                user.getUserId(),
                triggerReason);

        String model = openAiClient.getModel();
        String inputHash = hashInput(user, profile, behaviorSummary, snapshots, candidateFitSignals, model);
        Optional<StockAiSuggestionBatch> existingInputBatch = batchRepository
                .findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash,
                        REUSABLE_INPUT_STATUSES
                );

        if (existingInputBatch.isPresent()) {
            log.info("AI suggestion generation skipped for userId={} triggerReason={} promptVersion={} because input_hash is unchanged batchId={}",
                    user.getUserId(), triggerReason, PROMPT_VERSION, existingInputBatch.get().getSuggestionBatchId());
            return buildResponse(user, existingInputBatch.get(), "Returned existing suggestions because your profile and stock data are unchanged.", false);
        }

        GenerationResult generationResult = generateWithOpenAi(profile, behaviorSummary, snapshots, candidateFitSignals);
        if (generationResult.success()) {
            StockAiSuggestionBatch saved = saveBatchAndItems(
                    user,
                    profile,
                    snapshots,
                    inputHash,
                    model,
                    StockAiSuggestionBatchStatus.SUCCESS,
                    triggerReason,
                    generationResult.content(),
                    generationResult.openAiResult(),
                    null
            );
            log.info("Generated AI suggestion batch userId={} batchId={} triggerReason={} promptVersion={}",
                    user.getUserId(), saved.getSuggestionBatchId(), triggerReason, PROMPT_VERSION);
            return buildResponse(user, saved, "Generated new AI stock suggestions", false);
        }

        log.info("AI suggestion OpenAI generation failed for userId={} triggerReason={} error={}",
                user.getUserId(), triggerReason, generationResult.errorMessage());
        Optional<StockAiSuggestionBatch> cached = batchRepository
                .findTopByUserUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        user.getUserId(),
                        StockAiSuggestionBatchStatus.SUCCESS,
                        now.minusDays(3)
                );
        if (cached.isPresent()) {
            StockAiSuggestionBatch fallbackCachedBatch = saveOrReuseFallbackCachedBatch(
                    user,
                    profile,
                    inputHash,
                    model,
                    triggerReason,
                    cached.get(),
                    generationResult.errorMessage()
            );
            log.info("AI suggestion fallback cache used for userId={} cachedBatchId={} fallbackCachedBatchId={} triggerReason={}",
                    user.getUserId(),
                    cached.get().getSuggestionBatchId(),
                    fallbackCachedBatch.getSuggestionBatchId(),
                    triggerReason);
            return buildResponse(fallbackCachedBatch.getUser(), fallbackCachedBatch, "AI suggestions are temporarily unavailable, so the latest cached suggestions are shown.", true);
        }

        AiSuggestionContentDto fallback = buildRuleBasedFallback(profile, snapshots);
        StockAiSuggestionBatch fallbackBatch = saveBatchAndItems(
                user,
                profile,
                snapshots,
                inputHash,
                model,
                StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED,
                triggerReason,
                fallback,
                null,
                generationResult.errorMessage()
        );
        log.info("AI suggestion rule-based fallback saved for userId={} batchId={} triggerReason={}",
                user.getUserId(), fallbackBatch.getSuggestionBatchId(), triggerReason);
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

    private GenerationResult generateWithOpenAi(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots,
            List<CandidateFitSignal> candidateFitSignals
    ) {
        String userContent = buildPromptInput(profile, behaviorSummary, snapshots, candidateFitSignals, null);
        OpenAiSuggestionResult firstResult = openAiClient.generateSuggestion(SYSTEM_PROMPT, userContent);
        GenerationResult firstParsed = parseAndValidate(firstResult, profile, snapshots);
        if (firstParsed.success()) {
            return firstParsed;
        }

        log.info("AI suggestion validation failed before retry: {}", firstParsed.errorMessage());
        String retryContent = buildPromptInput(profile, behaviorSummary, snapshots, candidateFitSignals, firstParsed.errorMessage());
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
        if (factorCount < 3) {
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
        Optional<StockAiSuggestionBatch> existingFallbackCached = (status == StockAiSuggestionBatchStatus.SUCCESS
                || status == StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED)
                ? batchRepository.findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash
                ).filter(batch -> batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_CACHED)
                : Optional.empty();

        StockAiSuggestionBatch savedBatch;
        List<StockAiSuggestionItem> previousBatchItems = List.of();
        if (existingFallbackCached.isPresent()) {
            previousBatchItems = itemRepository.findBySuggestionBatchAndStatusInOrderByRankNoAsc(
                    existingFallbackCached.get(),
                    TOP_ITEM_STATUSES
            );
            expirePreviousActiveItems(user.getUserId(), now);
            savedBatch = upgradeFallbackCachedBatch(
                    existingFallbackCached.get(),
                    profile,
                    status,
                    triggerReason,
                    content,
                    openAiResult,
                    errorMessage,
                    now
            );
        } else {
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
            savedBatch = batchRepository.save(batch);
        }

        saveOrUpdateSuggestionItems(user, snapshots, content, savedBatch, previousBatchItems, now);
        return savedBatch;
    }

    private void saveOrUpdateSuggestionItems(
            AppUser user,
            List<StockAnalysisSnapshot> snapshots,
            AiSuggestionContentDto content,
            StockAiSuggestionBatch savedBatch,
            List<StockAiSuggestionItem> previousBatchItems,
            LocalDateTime now
    ) {
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
                .collect(Collectors.toMap(StockAnalysisSnapshot::getSymbol, Function.identity()));
        Map<String, StockAiSuggestionItem> previousItemBySymbol = previousBatchItems.stream()
                .collect(Collectors.toMap(
                        item -> normalizeSymbol(item.getSymbol()),
                        Function.identity(),
                        (first, second) -> first
                ));
        Set<String> newSymbols = content.suggestedStocks().stream()
                .map(stock -> normalizeSymbol(stock.symbol()))
                .collect(Collectors.toSet());
        for (StockAiSuggestionItem previousItem : previousBatchItems) {
            if (!newSymbols.contains(normalizeSymbol(previousItem.getSymbol()))) {
                previousItem.setStatus(StockAiSuggestionItemStatus.EXPIRED);
                previousItem.setUpdatedAt(now);
                itemRepository.save(previousItem);
            }
        }

        for (AiSuggestedStockDto suggestedStock : content.suggestedStocks()) {
            String symbol = normalizeSymbol(suggestedStock.symbol());
            StockAnalysisSnapshot snapshot = snapshotBySymbol.get(symbol);
            if (snapshot == null) {
                continue;
            }
            StockAiSuggestionItem item = previousItemBySymbol.getOrDefault(symbol, new StockAiSuggestionItem());
            if (item.getSuggestionItemId() == null) {
                item.setSuggestionBatch(savedBatch);
                item.setUser(user);
                item.setCreatedAt(now);
                item.setStatus(StockAiSuggestionItemStatus.ACTIVE);
            } else if (item.getStatus() != StockAiSuggestionItemStatus.WATCHLISTED) {
                item.setStatus(StockAiSuggestionItemStatus.ACTIVE);
            }
            item.setSymbol(symbol);
            item.setRankNo(suggestedStock.rankNo());
            item.setMatchScore(suggestedStock.matchScore());
            item.setRiskLevel(suggestedStock.riskLevel());
            item.setSuggestionLabel(suggestedStock.suggestionLabel());
            item.setShortReason(suggestedStock.shortReason());
            item.setDetailReason(suggestedStock.detailReason());
            item.setAnalysisSnapshot(snapshot);
            item.setUpdatedAt(now);
            itemRepository.save(item);
        }
    }

    private StockAiSuggestionBatch upgradeFallbackCachedBatch(
            StockAiSuggestionBatch batch,
            UserInvestmentProfile profile,
            StockAiSuggestionBatchStatus status,
            StockAiSuggestionTriggerReason triggerReason,
            AiSuggestionContentDto content,
            OpenAiSuggestionResult openAiResult,
            String errorMessage,
            LocalDateTime now
    ) {
        batch.setProfile(profile);
        batch.setProfileVersion(profile.getProfileVersion());
        batch.setStatus(status);
        batch.setTriggerReason(triggerReason);
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
        return batchRepository.save(batch);
    }

    private StockAiSuggestionBatch saveOrReuseFallbackCachedBatch(
            AppUser user,
            UserInvestmentProfile profile,
            String inputHash,
            String model,
            StockAiSuggestionTriggerReason triggerReason,
            StockAiSuggestionBatch cachedBatch,
            String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        Optional<StockAiSuggestionBatch> existingFallbackCached = batchRepository
                .findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash
                )
                .filter(batch -> batch.getStatus() == StockAiSuggestionBatchStatus.FALLBACK_CACHED);

        if (existingFallbackCached.isPresent()) {
            StockAiSuggestionBatch existing = existingFallbackCached.get();
            existing.setTriggerReason(triggerReason);
            existing.setErrorMessage(errorMessage);
            existing.setCreatedAt(now);
            existing.setUpdatedAt(now);
            existing.setExpiresAt(now.plusHours(BATCH_EXPIRY_HOURS));
            return batchRepository.save(existing);
        }

        List<StockAiSuggestionItem> cachedItems = itemRepository
                .findBySuggestionBatchAndStatusInOrderByRankNoAsc(cachedBatch, TOP_ITEM_STATUSES);
        expirePreviousActiveItems(user.getUserId(), now);
        StockAiSuggestionBatch batch = new StockAiSuggestionBatch();
        batch.setUser(user);
        batch.setProfile(profile);
        batch.setProfileVersion(profile.getProfileVersion());
        batch.setModel(model);
        batch.setPromptVersion(PROMPT_VERSION);
        batch.setStatus(StockAiSuggestionBatchStatus.FALLBACK_CACHED);
        batch.setTriggerReason(triggerReason);
        batch.setInputHash(inputHash);
        batch.setBatchSummary(cachedBatch.getBatchSummary());
        batch.setAnalysisTimeframe(ANALYSIS_TIMEFRAME);
        batch.setErrorMessage(errorMessage);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batch.setExpiresAt(now.plusHours(BATCH_EXPIRY_HOURS));
        StockAiSuggestionBatch savedBatch = batchRepository.save(batch);

        for (StockAiSuggestionItem cachedItem : cachedItems) {
            StockAiSuggestionItem item = new StockAiSuggestionItem();
            item.setSuggestionBatch(savedBatch);
            item.setUser(user);
            item.setSymbol(cachedItem.getSymbol());
            item.setRankNo(cachedItem.getRankNo());
            item.setMatchScore(cachedItem.getMatchScore());
            item.setRiskLevel(cachedItem.getRiskLevel());
            item.setSuggestionLabel(cachedItem.getSuggestionLabel());
            item.setShortReason(cachedItem.getShortReason());
            item.setDetailReason(cachedItem.getDetailReason());
            item.setAnalysisSnapshot(cachedItem.getAnalysisSnapshot());
            item.setStatus(cachedItem.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED
                    ? StockAiSuggestionItemStatus.WATCHLISTED
                    : StockAiSuggestionItemStatus.ACTIVE);
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

    private String buildPromptInput(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots,
            List<CandidateFitSignal> candidateFitSignals,
            String validationError
    ) {
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

        Map<String, Object> behaviorProfile = new LinkedHashMap<>();
        behaviorProfile.put("behaviorProfileId", valueOrBlank(behaviorSummary.behaviorProfileId()));
        behaviorProfile.put("analysisStartDate", valueOrBlank(toText(behaviorSummary.analysisStartDate())));
        behaviorProfile.put("analysisEndDate", valueOrBlank(toText(behaviorSummary.analysisEndDate())));
        behaviorProfile.put("behaviorRiskScore", valueOrBlank(behaviorSummary.behaviorRiskScore()));
        behaviorProfile.put("behaviorStyle", valueOrBlank(behaviorSummary.behaviorStyle()));
        behaviorProfile.put("behaviorConfidence", behaviorSummary.behaviorConfidence() == null ? BehaviorConfidence.LOW : behaviorSummary.behaviorConfidence());
        behaviorProfile.put("averagePositionSizePercent", valueOrBlank(behaviorSummary.averagePositionSizePercent()));
        behaviorProfile.put("turnoverLevel", valueOrBlank(behaviorSummary.turnoverLevel()));
        behaviorProfile.put("concentrationLevel", valueOrBlank(behaviorSummary.concentrationLevel()));
        behaviorProfile.put("highVolatilityExposure", valueOrBlank(behaviorSummary.highVolatilityExposure()));
        behaviorProfile.put("stockRiskExposureScore", valueOrBlank(behaviorSummary.stockRiskExposureScore()));
        behaviorProfile.put("concentrationScore", valueOrBlank(behaviorSummary.concentrationScore()));
        behaviorProfile.put("turnoverScore", valueOrBlank(behaviorSummary.turnoverScore()));
        behaviorProfile.put("holdingPeriodScore", valueOrBlank(behaviorSummary.holdingPeriodScore()));
        behaviorProfile.put("volatilityExposureScore", valueOrBlank(behaviorSummary.volatilityExposureScore()));
        behaviorProfile.put("favoriteRiskCategory", valueOrBlank(behaviorSummary.favoriteRiskCategory()));
        behaviorProfile.put("mostTradedSymbols", valueOrBlank(behaviorSummary.mostTradedSymbols()));
        behaviorProfile.put("behaviorSummaryText", valueOrBlank(behaviorSummary.behaviorSummaryText()));
        behaviorProfile.put("updatedAt", valueOrBlank(toText(behaviorSummary.updatedAt())));
        behaviorProfile.put("sourceNote", valueOrBlank(behaviorSummary.sourceNote()));
        behaviorProfile.put("personalizationRule", "LOW confidence is informational only; MEDIUM confidence may mildly adjust fit; HIGH confidence may meaningfully influence ranking. Conservative onboarding cannot be overridden by aggressive behavior unless behavior confidence is HIGH.");
        input.put("behaviorProfile", behaviorProfile);

        input.put("analysisTimeframe", ANALYSIS_TIMEFRAME);
        input.put("maxSuggestions", Math.min(MAX_SUGGESTIONS, snapshots.size()));
        input.put("supportedSymbols", SUPPORTED_SYMBOLS);
        input.put("candidateFitSignals", candidateFitSignals.stream()
                .map(this::candidateFitSignalToMap)
                .toList());
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

    private List<CandidateFitSignal> buildCandidateFitSignals(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots
    ) {
        return snapshots.stream()
                .sorted(Comparator.comparing(StockAnalysisSnapshot::getSymbol))
                .map(snapshot -> {
                    String riskCompatibility = riskCompatibility(profile, snapshot);
                    String behaviorCompatibility = behaviorCompatibility(profile, behaviorSummary, snapshot);
                    String dataQuality = dataQuality(snapshot);
                    int dataQualityPenalty = dataQualityPenalty(dataQuality);

                    return new CandidateFitSignal(
                            snapshot.getSymbol(),
                            valueOrBlank(snapshot.getRiskCategory()).toString(),
                            riskCompatibility,
                            behaviorCompatibility,
                            dataQuality,
                            dataQualityPenalty,
                            candidateFitNotes(riskCompatibility, behaviorSummary, dataQuality)
                    );
                })
                .toList();
    }

    private String riskCompatibility(UserInvestmentProfile profile, StockAnalysisSnapshot snapshot) {
        String riskCategory = snapshot.getRiskCategory();
        RiskTolerance riskTolerance = profile.getRiskTolerance() == null ? RiskTolerance.MODERATE : profile.getRiskTolerance();
        boolean moderateBeginner = riskTolerance == RiskTolerance.MODERATE
                && profile.getExperienceLevel() != null
                && profile.getExperienceLevel().name().equals("BEGINNER");

        if (riskTolerance == RiskTolerance.CONSERVATIVE) {
            if (equalsIgnoreCase(riskCategory, "conservative")) {
                return "MATCH";
            }
            if (equalsIgnoreCase(riskCategory, "moderate")) {
                return "PARTIAL_MATCH";
            }
            return "MISMATCH";
        }
        if (riskTolerance == RiskTolerance.AGGRESSIVE) {
            if (equalsIgnoreCase(riskCategory, "aggressive")) {
                return "MATCH";
            }
            return "PARTIAL_MATCH";
        }
        if (equalsIgnoreCase(riskCategory, "moderate")) {
            return "MATCH";
        }
        if (moderateBeginner && equalsIgnoreCase(riskCategory, "aggressive")) {
            return "MISMATCH";
        }
        return "PARTIAL_MATCH";
    }

    private String behaviorCompatibility(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            StockAnalysisSnapshot snapshot
    ) {
        BehaviorConfidence confidence = behaviorSummary.behaviorConfidence() == null
                ? BehaviorConfidence.LOW
                : behaviorSummary.behaviorConfidence();
        if (confidence == BehaviorConfidence.LOW || behaviorSummary.behaviorStyle() == null) {
            return "INSUFFICIENT_DATA";
        }
        if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                && confidence != BehaviorConfidence.HIGH
                && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")) {
            return "MISMATCH";
        }

        String aligned = behaviorStyleAlignment(behaviorSummary, snapshot);
        if (confidence == BehaviorConfidence.MEDIUM && "MATCH".equals(aligned)) {
            return "PARTIAL_MATCH";
        }
        return aligned;
    }

    private String behaviorStyleAlignment(BehaviorSummaryForSuggestion behaviorSummary, StockAnalysisSnapshot snapshot) {
        return switch (behaviorSummary.behaviorStyle()) {
            case CONSERVATIVE -> {
                if (equalsIgnoreCase(snapshot.getRiskCategory(), "conservative")) {
                    yield "MATCH";
                }
                if (equalsIgnoreCase(snapshot.getRiskCategory(), "moderate")) {
                    yield "PARTIAL_MATCH";
                }
                yield "MISMATCH";
            }
            case BALANCED -> equalsIgnoreCase(snapshot.getRiskCategory(), "moderate") ? "MATCH" : "PARTIAL_MATCH";
            case ACTIVE_TRADER, AGGRESSIVE -> {
                if (equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")) {
                    yield "MATCH";
                }
                if (equalsIgnoreCase(snapshot.getRiskCategory(), "moderate")) {
                    yield "PARTIAL_MATCH";
                }
                yield "MISMATCH";
            }
            case INSUFFICIENT_DATA -> "INSUFFICIENT_DATA";
        };
    }

    private String dataQuality(StockAnalysisSnapshot snapshot) {
        int missingDataCount = snapshot.getMissingDataCount() == null ? 0 : snapshot.getMissingDataCount();
        if (missingDataCount >= 3) {
            return "WEAK";
        }
        if (Boolean.TRUE.equals(snapshot.getIsFallback()) || missingDataCount > 0) {
            return "PARTIAL";
        }
        return "COMPLETE";
    }

    private int dataQualityPenalty(String dataQuality) {
        return switch (dataQuality) {
            case "WEAK" -> 25;
            case "PARTIAL" -> 10;
            default -> 0;
        };
    }

    private List<String> candidateFitNotes(
            String riskCompatibility,
            BehaviorSummaryForSuggestion behaviorSummary,
            String dataQuality
    ) {
        List<String> notes = new ArrayList<>();
        notes.add(switch (riskCompatibility) {
            case "MATCH" -> "Matches onboarding risk profile";
            case "PARTIAL_MATCH" -> "Partially matches onboarding risk profile";
            default -> "Does not closely match onboarding risk profile";
        });
        BehaviorConfidence confidence = behaviorSummary.behaviorConfidence() == null
                ? BehaviorConfidence.LOW
                : behaviorSummary.behaviorConfidence();
        notes.add("Behavior profile confidence is " + confidence);
        notes.add(switch (dataQuality) {
            case "WEAK" -> "Recent data has notable gaps";
            case "PARTIAL" -> "Recent data is partially complete";
            default -> "Recent data is complete";
        });
        return notes;
    }

    private Map<String, Object> candidateFitSignalToMap(CandidateFitSignal signal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", signal.symbol());
        map.put("riskCategory", signal.riskCategory());
        map.put("riskCompatibility", signal.riskCompatibility());
        map.put("behaviorCompatibility", signal.behaviorCompatibility());
        map.put("dataQuality", signal.dataQuality());
        map.put("dataQualityPenalty", signal.dataQualityPenalty());
        map.put("fitNotes", signal.fitNotes());
        return map;
    }

    private List<String> candidateFitSignals(List<CandidateFitSignal> signals) {
        return signals.stream()
                .sorted(Comparator.comparing(CandidateFitSignal::symbol))
                .map(signal -> signal.symbol()
                        + ":risk=" + signal.riskCompatibility()
                        + ",behavior=" + signal.behaviorCompatibility()
                        + ",data=" + signal.dataQuality())
                .toList();
    }

    private Object valueOrBlank(Object value) {
        return value == null ? "" : value;
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String hashInput(
            AppUser user,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots,
            List<CandidateFitSignal> candidateFitSignals,
            String model
    ) {
        String snapshotHashPart = snapshots.stream()
                .sorted(Comparator.comparing(StockAnalysisSnapshot::getSymbol))
                .map(snapshot -> snapshot.getSymbol() + ":" + snapshot.getSnapshotHash())
                .collect(Collectors.joining("|"));
        String candidateFitPart = candidateFitSignals.stream()
                .sorted(Comparator.comparing(CandidateFitSignal::symbol))
                .map(signal -> String.join(":",
                        signal.symbol(),
                        signal.riskCategory(),
                        signal.riskCompatibility(),
                        signal.behaviorCompatibility(),
                        signal.dataQuality(),
                        String.valueOf(signal.dataQualityPenalty()),
                        String.join(",", signal.fitNotes())
                ))
                .collect(Collectors.joining("|"));
        String raw = String.join("|",
                String.valueOf(user.getUserId()),
                String.valueOf(profile.getProfileId()),
                String.valueOf(profile.getProfileVersion()),
                String.valueOf(profile.getRiskTolerance()),
                String.valueOf(profile.getInvestmentGoal()),
                String.valueOf(profile.getExperienceLevel()),
                String.valueOf(profile.getPreferredVolatility()),
                String.valueOf(profile.getPreferredHorizon()),
                String.valueOf(profile.getRiskScore()),
                String.valueOf(profile.getGoalScore()),
                String.valueOf(profile.getExperienceScore()),
                String.valueOf(profile.getProfileSource()),
                String.valueOf(profile.getBehaviorRiskScore()),
                String.valueOf(profile.getBehaviorStyle()),
                String.valueOf(profile.getBehaviorConfidence()),
                String.valueOf(behaviorSummary.behaviorProfileId()),
                String.valueOf(behaviorSummary.behaviorRiskScore()),
                String.valueOf(behaviorSummary.behaviorStyle()),
                String.valueOf(behaviorSummary.behaviorConfidence()),
                String.valueOf(behaviorSummary.averagePositionSizePercent()),
                String.valueOf(behaviorSummary.turnoverLevel()),
                String.valueOf(behaviorSummary.concentrationLevel()),
                String.valueOf(behaviorSummary.highVolatilityExposure()),
                String.valueOf(behaviorSummary.stockRiskExposureScore()),
                String.valueOf(behaviorSummary.concentrationScore()),
                String.valueOf(behaviorSummary.turnoverScore()),
                String.valueOf(behaviorSummary.holdingPeriodScore()),
                String.valueOf(behaviorSummary.volatilityExposureScore()),
                String.valueOf(behaviorSummary.favoriteRiskCategory()),
                String.valueOf(behaviorSummary.mostTradedSymbols()),
                String.valueOf(behaviorSummary.behaviorSummaryText()),
                String.valueOf(behaviorSummary.updatedAt()),
                candidateFitPart,
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

    private record CandidateFitSignal(
            String symbol,
            String riskCategory,
            String riskCompatibility,
            String behaviorCompatibility,
            String dataQuality,
            int dataQualityPenalty,
            List<String> fitNotes
    ) {
    }

    private record RefreshCooldown(boolean refreshAllowed, LocalDateTime nextRefreshAllowedAt) {
    }
}
