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
import net.boyuan.stockmentor.market.stock.dto.DelayedPriceMetadataResponse;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import net.boyuan.stockmentor.userprofile.model.PreferredVolatility;
import net.boyuan.stockmentor.userprofile.model.RiskTolerance;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
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
    private final DelayedMarketPriceService delayedMarketPriceService;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_VERSION = "stock-suggestion-v3";
    private static final String ANALYSIS_TIMEFRAME = "7D";
    private static final int MAX_SUGGESTIONS = 3;
    private static final int MANUAL_REFRESH_COOLDOWN_HOURS = 1;
    private static final int BATCH_EXPIRY_HOURS = 24;
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.stream(StockMetadata.SYMBOLS.split(","))
            .map(String::trim)
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .toList();
    private static final String SUPPORTED_SYMBOLS_VERSION = String.join(",", SUPPORTED_SYMBOLS);
    private static final List<StockAiSuggestionBatchStatus> READABLE_BATCH_STATUSES = List.of(
            StockAiSuggestionBatchStatus.SUCCESS,
            StockAiSuggestionBatchStatus.FALLBACK_CACHED,
            StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED
    );
    private static final List<StockAiSuggestionBatchStatus> REUSABLE_INPUT_STATUSES = List.of(
            StockAiSuggestionBatchStatus.SUCCESS
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
            * explicit personalization weights that control how declared onboarding preferences and observed paper-trading behavior should be blended
            
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
            * Treat the onboarding profile as the user's declared preference from the onboarding quiz.
            * Treat the behavior profile as observed behavior from simulated paper trading only.
            * Use personalizationWeight.onboardingWeight and personalizationWeight.behaviorWeight to decide how strongly each signal should influence ranking.
            * If behavior confidence is LOW or no behavior profile exists, behavior is informational only and must not override onboarding.
            * If behavior confidence is MEDIUM, behavior may meaningfully adjust close rankings.
            * If behavior confidence is HIGH, observed paper-trading behavior may become the stronger personalization signal.
            * If HIGH-confidence behavior conflicts with onboarding, explain the conflict in simple educational language.
            * Prefer stocks whose final risk category and volatility fit the user profile.
            * Use the behavior summary as a secondary adjustment, not as the only decision factor.
            * If behaviorConfidence is LOW or MEDIUM, do not let behavior summary override the user's main risk tolerance, experience level, and preferred volatility.
            * If behaviorConfidence is HIGH, behavior summary may have stronger influence, but the explanation must clearly state why.
            * When two stocks have similar risk and volatility fit, rank the stock with better-supported movement evidence and less erratic price consistency higher.
            * For growth-oriented users, an uptrend can be preferred over sideways movement when risk and volatility are still suitable.
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
            * A stock with highly erratic price consistency should normally rank below a similar stock with less erratic price consistency for beginner users.
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
            * This score order is mandatory: rank 1 matchScore must be greater than or equal to rank 2, and rank 2 must be greater than or equal to rank 3.
            * If two stocks have the same score, the stock with stronger behavior fit, clearer movement, or better data quality should be ranked first.
            * Do not place a lower-score stock above a higher-score stock.
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
            * candidateFitSignals.combinedFitScore is backend ranking guidance created from onboarding fit, behavior fit, data quality, and movement quality.
            * Use candidateFitSignals.combinedFitScore as the primary ranking anchor unless the stockSnapshots clearly justify a small adjustment.
            * The final selected stocks should normally follow combinedFitScore order.
            * matchScore should be close to the candidateFitSignals.combinedFitScore, but may be adjusted by about 0 to 5 points when the explanation clearly supports the adjustment.
            * Do not select a stock with combinedFitScore below 60 when at least 3 stocks have combinedFitScore 60 or above.
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
            * Good suggestionLabel examples: "Risk-aligned paper-trading practice", "Choppy movement observation", "Volatility comparison practice", "Data-quality-aware practice", "Lower-risk behavior fit" and others.
            * detailReason should be around 40 to 70 words.
            * detailReason must mention at least two provided factors, ideally three or more, such as risk category, volatility, trend, price consistency, volume trend, behavior confidence, or data quality.
            * Natural synonyms are allowed. Do not force a checklist sentence.
            * Avoid phrases like "growth-oriented trading", "stable learning environment", and "good choice".
            * Prefer "paper-trading practice", "educational example", "observing movement", and "matches the provided profile".
            * Do not make the reason sound like real trading advice.
            * Use wording such as "paper-trading practice", "learning example", "easier to observe", "matches the provided profile", and "educational fit".
            * Avoid trading-strategy terms such as "range trading", "trend-following", or "breakout" unless they are provided in the input.
            * Use simpler wording such as "sideways movement practice", "observing choppy movement", or "learning from provided price movement".
            * The reason must not contradict the provided trend, volatilityLabel, volumeTrend, or priceConsistency.
            * Do not say "consistent", "smooth", or "steady" when the provided priceConsistency is choppy or highly erratic.
            * If the stock has choppy or erratic movement, clearly mention that as a learning factor.
            * Do not write awkward repeated wording such as "uptrend trend" or "downtrend trend"; use "trend is ..." or "trend label is ..." instead.
            * detailReason must be specific and factor-based; mention enough provided factors for backend validation.
            * Do not expose backend field names in user-facing explanations. Avoid raw terms such as riskCategory, volatilityLabel, priceConsistency, volumeTrend, dataQualityLabel, behaviorCompatibility, behaviorFitScore, combinedFitScore, MATCH, PARTIAL_MATCH, COMPLETE, or LOW/HIGH as technical labels.
            * Convert backend fields into natural beginner wording. For example, write "conservative risk level" instead of "riskCategory is conservative", "low volatility" instead of "volatilityLabel is low", and "complete data" instead of "dataQuality is COMPLETE".
            * You may mention behavior confidence naturally, such as "recent paper-trading behavior is strong enough to influence this suggestion", but do not write raw backend terms like behaviorConfidence HIGH.
            
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
                  "suggestionLabel": "Risk-aligned paper-trading practice",
                  "shortReason": "One sentence reason.",
                  "detailReason": "Beginner-friendly explanation using at least three provided factors."
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

        BehaviorSummaryForSuggestion behaviorSummary = behaviorSummaryOrLowNoData(
                behaviorProfileService.getBehaviorSummaryForSuggestion(user.getUserId())
        );
        PersonalizationWeight personalizationWeight = personalizationWeight(behaviorSummary);
        List<CandidateFitSignal> candidateFitSignals = buildCandidateFitSignals(profile, behaviorSummary, personalizationWeight, snapshots);
        log.info("AI suggestion generation promptVersion={} behaviorConfidence={} behaviorRiskScore={} onboardingWeight={} behaviorWeight={} candidateFitSignals={} userId={} triggerReason={}",
                PROMPT_VERSION,
                behaviorSummary.behaviorConfidence(),
                behaviorSummary.behaviorRiskScore(),
                personalizationWeight.onboardingWeight(),
                personalizationWeight.behaviorWeight(),
                candidateFitSignals(candidateFitSignals),
                user.getUserId(),
                triggerReason);

        String model = openAiClient.getModel();
        String inputHash = hashInput(user, profile, behaviorSummary, personalizationWeight, snapshots, candidateFitSignals, model);
        Optional<StockAiSuggestionBatch> existingInputBatch = batchRepository
                .findTopByUserUserIdAndModelAndPromptVersionAndInputHashAndStatusInOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash,
                        REUSABLE_INPUT_STATUSES
                );

        if (existingInputBatch.isPresent()) {
            StockAiSuggestionBatch reusableBatch = refreshReusableInputBatchIfNeeded(existingInputBatch.get(), now);
            log.info("AI suggestion generation skipped for userId={} triggerReason={} promptVersion={} because input_hash is unchanged batchId={}",
                    user.getUserId(), triggerReason, PROMPT_VERSION, reusableBatch.getSuggestionBatchId());
            return buildResponse(user, reusableBatch, "Returned existing suggestions because your profile and stock data are unchanged.", false);
        }

        GenerationResult generationResult = generateWithOpenAi(profile, behaviorSummary, personalizationWeight, snapshots, candidateFitSignals);
        if (generationResult.success()) {
            StockAiSuggestionBatch saved = saveOrUpdateSuccessBatch(
                    user,
                    profile,
                    snapshots,
                    inputHash,
                    model,
                    triggerReason,
                    generationResult.content(),
                    generationResult.openAiResult()
            );
            log.info("Generated AI suggestion batch userId={} batchId={} triggerReason={} promptVersion={}",
                    user.getUserId(), saved.getSuggestionBatchId(), triggerReason, PROMPT_VERSION);
            return buildResponse(user, saved, "Generated new AI stock suggestions", false);
        }

        log.info("AI suggestion OpenAI generation failed for userId={} triggerReason={} error={}",
                user.getUserId(), triggerReason, generationResult.errorMessage());
        AiSuggestionContentDto fallback = buildRuleBasedFallback(profile, behaviorSummary, personalizationWeight, snapshots, candidateFitSignals);
        StockAiSuggestionBatch fallbackBatch = saveOrUpdateRuleBasedFallbackBatch(
                user,
                profile,
                snapshots,
                inputHash,
                model,
                triggerReason,
                fallback,
                generationResult.errorMessage()
        );
        log.info("AI suggestion rule-based fallback saved for userId={} batchId={} triggerReason={}",
                user.getUserId(), fallbackBatch.getSuggestionBatchId(), triggerReason);
        boolean fallbackUsed = fallbackBatch.getStatus() != StockAiSuggestionBatchStatus.SUCCESS;
        String message = fallbackUsed
                ? "AI suggestions are temporarily unavailable, so a simple rule-based fallback is shown."
                : "Returned existing suggestions because your profile and stock data are unchanged.";
        return buildResponse(user, fallbackBatch, message, fallbackUsed);
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
            PersonalizationWeight personalizationWeight,
            List<StockAnalysisSnapshot> snapshots,
            List<CandidateFitSignal> candidateFitSignals
    ) {
        String userContent = buildPromptInput(profile, behaviorSummary, personalizationWeight, snapshots, candidateFitSignals, null);
        OpenAiSuggestionResult firstResult = openAiClient.generateSuggestion(SYSTEM_PROMPT, userContent);
        GenerationResult firstParsed = parseAndValidate(firstResult, profile, behaviorSummary, snapshots);
        if (firstParsed.success()) {
            return firstParsed;
        }

        log.info("AI suggestion validation failed before retry: {}", firstParsed.errorMessage());
        String retryContent = buildPromptInput(profile, behaviorSummary, personalizationWeight, snapshots, candidateFitSignals, firstParsed.errorMessage());
        OpenAiSuggestionResult retryResult = openAiClient.generateSuggestion(SYSTEM_PROMPT, retryContent);
        return parseAndValidate(retryResult, profile, behaviorSummary, snapshots);
    }

    private GenerationResult parseAndValidate(
            OpenAiSuggestionResult result,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots
    ) {
        if (!result.success()) {
            return GenerationResult.failure(result.errorMessage());
        }

        try {
            AiSuggestionContentDto rawContent = objectMapper.readValue(cleanJson(result.content()), AiSuggestionContentDto.class);
            AiSuggestionContentDto normalizedContent = normalizeAiContent(rawContent, profile, behaviorSummary, snapshots);
            validateAiContent(normalizedContent, profile, behaviorSummary, snapshots);
            return GenerationResult.success(normalizedContent, result);
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

    private AiSuggestionContentDto normalizeAiContent(
            AiSuggestionContentDto content,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots
    ) {
        if (content == null || content.suggestedStocks() == null) {
            return content;
        }

        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
                .collect(Collectors.toMap(
                        snapshot -> normalizeSymbol(snapshot.getSymbol()),
                        Function.identity(),
                        (first, second) -> first
                ));

        List<AiSuggestedStockDto> normalizedStocks = content.suggestedStocks().stream()
                .map(stock -> {
                    StockAnalysisSnapshot snapshot = snapshotBySymbol.get(normalizeSymbol(stock.symbol()));
                    if (snapshot == null) {
                        return stock;
                    }
                    return normalizeSuggestedStock(stock, profile, behaviorSummary, snapshot);
                })
                .toList();

        return new AiSuggestionContentDto(
                sanitizeReasonText(content.batchSummary()),
                normalizedStocks
        );
    }

    private String appendBehaviorContextIfNeeded(
            String detailReason,
            String shortReason,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            AiSuggestedStockDto stock,
            StockAnalysisSnapshot snapshot
    ) {
        if (!shouldAppendBehaviorContext(profile, behaviorSummary, stock, snapshot)) {
            return detailReason;
        }

        String combined = valueOrBlank(shortReason) + " " + valueOrBlank(detailReason);
        if (mentionsOnboardingBehaviorConflictText(combined)) {
            return detailReason;
        }

        return appendSentence(detailReason, behaviorContextSentence(profile, behaviorSummary, snapshot));
    }

    private boolean shouldAppendBehaviorContext(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            AiSuggestedStockDto stock,
            StockAnalysisSnapshot snapshot
    ) {
        if (behaviorConfidence(behaviorSummary) != BehaviorConfidence.HIGH) {
            return false;
        }

        String declaredPreference = declaredRiskPreference(profile);
        String observedPreference = behaviorRiskPreference(behaviorSummary);

        if (declaredPreference.equals(observedPreference)) {
            return false;
        }

        int score = stock.matchScore() == null ? 0 : stock.matchScore();
        boolean selectedFollowsBehavior = "MATCH".equals(riskPreferenceAlignment(observedPreference, snapshot));
        boolean selectedConflictsWithOnboarding = "MISMATCH".equals(riskPreferenceAlignment(declaredPreference, snapshot));

        if (selectedFollowsBehavior && selectedConflictsWithOnboarding) {
            return true;
        }

        return score >= 80
                && "MISMATCH".equals(riskPreferenceAlignment(observedPreference, snapshot))
                && "MATCH".equals(riskPreferenceAlignment(declaredPreference, snapshot));
    }

    private String behaviorContextSentence(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            StockAnalysisSnapshot snapshot
    ) {
        String declaredPreference = declaredRiskPreference(profile);
        String observedPreference = behaviorRiskPreference(behaviorSummary);

        if ("conservative".equals(observedPreference) && "aggressive".equals(declaredPreference)) {
            return "Your recent paper-trading behavior leans lower-risk than your onboarding profile, so this suggestion gives more weight to observed practice behavior.";
        }

        if ("aggressive".equals(observedPreference) && "conservative".equals(declaredPreference)) {
            return "Your recent paper-trading behavior leans higher-risk than your onboarding profile, so this is presented only as an educational paper-trading example.";
        }

        return "Your observed paper-trading behavior differs from your onboarding profile, so this suggestion separates the declared preference from recent practice behavior.";
    }

    private String declaredRiskPreference(UserInvestmentProfile profile) {
        RiskTolerance riskTolerance = profile.getRiskTolerance() == null
                ? RiskTolerance.MODERATE
                : profile.getRiskTolerance();

        return switch (riskTolerance) {
            case CONSERVATIVE -> "conservative";
            case AGGRESSIVE -> "aggressive";
            default -> "moderate";
        };
    }

    private String appendSentence(String base, String sentence) {
        if (isBlank(sentence)) {
            return base;
        }
        if (isBlank(base)) {
            return sentence.trim();
        }

        String trimmedBase = base.trim();
        String trimmedSentence = sentence.trim();

        if (trimmedBase.contains(trimmedSentence)) {
            return trimmedBase;
        }

        if (trimmedBase.endsWith(".") || trimmedBase.endsWith("!") || trimmedBase.endsWith("?")) {
            return trimmedBase + " " + trimmedSentence;
        }

        return trimmedBase + ". " + trimmedSentence;
    }

    private AiSuggestedStockDto normalizeSuggestedStock(
            AiSuggestedStockDto stock,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            StockAnalysisSnapshot snapshot
    ) {
        String suggestionLabel = sanitizeReasonText(stock.suggestionLabel());
        String shortReason = sanitizeReasonText(stock.shortReason());
        String detailReason = sanitizeReasonText(stock.detailReason());

        detailReason = appendBehaviorContextIfNeeded(
                detailReason,
                shortReason,
                profile,
                behaviorSummary,
                stock,
                snapshot
        );

        return new AiSuggestedStockDto(
                normalizeSymbol(stock.symbol()),
                stock.rankNo(),
                stock.matchScore(),
                stock.riskLevel(),
                suggestionLabel,
                shortReason,
                detailReason
        );
    }

    private String sanitizeReasonText(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.replaceAll("\\s+", " ").trim();

        cleaned = cleaned.replaceAll("(?i)\\bvolatile downtrend trend\\b", "volatile downtrend");
        cleaned = cleaned.replaceAll("(?i)\\bvolatile uptrend trend\\b", "volatile uptrend");
        cleaned = cleaned.replaceAll("(?i)\\bdowntrend trend\\b", "downtrend");
        cleaned = cleaned.replaceAll("(?i)\\buptrend trend\\b", "uptrend");
        cleaned = cleaned.replaceAll("(?i)\\btrend is sideways\\b", "sideways movement");
        cleaned = cleaned.replaceAll("(?i)\\btrend is volatile downtrend\\b", "volatile downtrend");
        cleaned = cleaned.replaceAll("(?i)\\btrend is volatile uptrend\\b", "volatile uptrend");

        cleaned = cleaned.replaceAll("(?i)\\briskCategory\\b", "risk category");
        cleaned = cleaned.replaceAll("(?i)\\bvolatilityLabel\\b", "volatility");
        cleaned = cleaned.replaceAll("(?i)\\bpriceConsistency\\b", "price consistency");
        cleaned = cleaned.replaceAll("(?i)\\bvolumeTrend\\b", "volume trend");
        cleaned = cleaned.replaceAll("(?i)\\bdataQualityLabel\\b", "data quality");
        cleaned = cleaned.replaceAll("(?i)\\bdataQuality\\b", "data quality");
        cleaned = cleaned.replaceAll("(?i)\\bbehaviorCompatibility\\b", "behavior fit");
        cleaned = cleaned.replaceAll("(?i)\\bbehaviorConfidence\\b", "behavior confidence");
        cleaned = cleaned.replaceAll("(?i)\\bcombinedFitScore\\b", "backend fit score");

        cleaned = cleaned.replaceAll("\\bCOMPLETE\\b", "complete");
        cleaned = cleaned.replaceAll("\\bPARTIAL_MATCH\\b", "partial match");
        cleaned = cleaned.replaceAll("\\bMATCH\\b", "match");

        return cleaned;
    }

    private void validateAiContent(
            AiSuggestionContentDto content,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots
    ) {
        if (content == null || content.suggestedStocks() == null || content.suggestedStocks().isEmpty()) {
            throw new IllegalArgumentException("AI returned no suggested stocks");
        }
        if (content.suggestedStocks().size() > Math.min(MAX_SUGGESTIONS, snapshots.size())) {
            throw new IllegalArgumentException("AI returned too many suggested stocks");
        }

        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
        .collect(Collectors.toMap(
                snapshot -> normalizeSymbol(snapshot.getSymbol()),
                Function.identity(),
                (first, second) -> first
        ));
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
            if (isBlank(stock.suggestionLabel()) || isBlank(stock.shortReason()) || isBlank(stock.detailReason())) {
                throw new IllegalArgumentException("AI returned blank suggestion reason");
            }
            validateReasonText(stock, snapshot);
            validateDataQualityScore(stock, snapshot, snapshots);
        }
        validateMatchScoreOrder(content);
        validateSuggestedScoreFloor(content, snapshots);
        validateRiskReasonableness(content, profile, behaviorSummary, snapshots);
    }

    private void validateMatchScoreOrder(AiSuggestionContentDto content) {
        List<AiSuggestedStockDto> stocks = content.suggestedStocks();
        for (int i = 1; i < stocks.size(); i++) {
            Integer previousScore = stocks.get(i - 1).matchScore();
            Integer currentScore = stocks.get(i).matchScore();

            if (previousScore != null && currentScore != null && currentScore > previousScore) {
                throw new IllegalArgumentException("AI match scores do not follow rank order");
            }
        }
    }

    private void validateSuggestedScoreFloor(
            AiSuggestionContentDto content,
            List<StockAnalysisSnapshot> snapshots
    ) {
        if (snapshots.size() < MAX_SUGGESTIONS || content.suggestedStocks().size() < MAX_SUGGESTIONS) {
            return;
        }

        boolean hasWeakSuggestion = content.suggestedStocks().stream()
                .anyMatch(stock -> stock.matchScore() != null && stock.matchScore() < 60);

        if (hasWeakSuggestion) {
            throw new IllegalArgumentException("AI suggested a weak match score despite enough alternatives");
        }
    }

    private void validateReasonText(AiSuggestedStockDto stock, StockAnalysisSnapshot snapshot) {
        String suggestionLabel = stock.suggestionLabel();
        String shortReason = stock.shortReason();
        String detailReason = stock.detailReason();
        String combined = (suggestionLabel + " " + shortReason + " " + detailReason).toLowerCase(Locale.ROOT);
        List<String> banned = List.of(
                "you should buy", "you should sell", "should buy", "should sell",
                "guaranteed", "guarantee", "will rise", "will increase", "will go up",
                "will fall", "will recover", "will outperform", "expected return",
                "future price", "news", "earnings", "sector", "sure win", "must buy",
                "safe profit", "will definitely rise", "risk-free return", "risk free return"
        );
        if (banned.stream().anyMatch(combined::contains)) {
            throw new IllegalArgumentException("AI returned advice, prediction, or unsupported external reason");
        }
        if (containsDebugFieldName(combined)) {
            throw new IllegalArgumentException("AI returned backend field names in user-facing reason");
        }

        validateDetailReasonQuality(detailReason, snapshot);
        validateSnapshotWordingConsistency(suggestionLabel, shortReason, detailReason, snapshot);
    }

    private boolean containsDebugFieldName(String value) {
        return containsAny(
                value,
                "riskcategory",
                "volatilitylabel",
                "priceconsistency",
                "volumetrend",
                "dataqualitylabel",
                "behaviorcompatibility",
                "behaviorfitscore",
                "combinedfitscore"
        );
    }

    private void validateDetailReasonQuality(String detailReason, StockAnalysisSnapshot snapshot) {
        int wordCount = wordCount(detailReason);
        if (wordCount < 8) {
            throw new IllegalArgumentException("AI detail reason is too short");
        }
        if (wordCount > 120) {
            throw new IllegalArgumentException("AI detail reason is too long");
        }

        Set<String> factorGroups = mentionedFactorGroups(detailReason, snapshot);
        if (factorGroups.size() < 2) {
            throw new IllegalArgumentException("AI detail reason does not mention enough input factors");
        }
    }

    private Set<String> mentionedFactorGroups(String detailReason, StockAnalysisSnapshot snapshot) {
        String detail = detailReason == null ? "" : detailReason.toLowerCase(Locale.ROOT);
        Set<String> groups = new HashSet<>();

        String riskCategory = valueOrBlank(snapshot.getRiskCategory()).toString().toLowerCase(Locale.ROOT);
        String volatility = valueOrBlank(snapshot.getVolatilityLabel()).toString().toLowerCase(Locale.ROOT);
        String trend = valueOrBlank(snapshot.getTrend()).toString().toLowerCase(Locale.ROOT);
        String priceConsistency = valueOrBlank(snapshot.getPriceConsistency()).toString().toLowerCase(Locale.ROOT);
        String volumeTrend = valueOrBlank(snapshot.getVolumeTrend()).toString().toLowerCase(Locale.ROOT);

        if (containsAny(detail, "risk", "risk category") || (!riskCategory.isBlank() && detail.contains(riskCategory))) {
            groups.add("risk");
        }

        if (containsAny(detail, "volatility", "volatile") || (!volatility.isBlank() && detail.contains(volatility))) {
            groups.add("volatility");
        }

        if (containsAny(detail, "trend", "uptrend", "downtrend", "sideways", "movement", "direction")
                || (!trend.isBlank() && detail.contains(trend))) {
            groups.add("trend");
        }

        if (containsAny(detail, "price consistency", "choppy", "erratic", "smooth", "steady", "consistent")
                || (!priceConsistency.isBlank() && detail.contains(priceConsistency))) {
            groups.add("priceConsistency");
        }

        if (containsAny(detail, "volume", "trading activity")
                || (!volumeTrend.isBlank() && detail.contains(volumeTrend))) {
            groups.add("volume");
        }

        if (containsAny(detail, "data", "data quality", "complete", "fallback", "missing")) {
            groups.add("dataQuality");
        }

        return groups;
    }

    private int wordCount(String value) {
        if (isBlank(value)) {
            return 0;
        }
        return value.trim().split("\\s+").length;
    }

    private void validateSnapshotWordingConsistency(
            String suggestionLabel,
            String shortReason,
            String detailReason,
            StockAnalysisSnapshot snapshot
    ) {
        String combined = (suggestionLabel + " " + shortReason + " " + detailReason).toLowerCase(Locale.ROOT);
        String trend = valueOrBlank(snapshot.getTrend()).toString().toLowerCase(Locale.ROOT);
        String priceConsistency = valueOrBlank(snapshot.getPriceConsistency()).toString().toLowerCase(Locale.ROOT);

        boolean saysSteadySmoothOrConsistent = containsAny(combined, "steady", "smooth", "consistent");
        if (saysSteadySmoothOrConsistent
                && (containsAny(priceConsistency, "choppy", "erratic") || trend.contains("volatile"))) {
            throw new IllegalArgumentException("AI wording contradicts volatile or choppy snapshot data");
        }

        boolean saysStablePriceMovementOrTrend = containsAny(combined, "stable movement", "stable trend", "stable price");
        if (saysStablePriceMovementOrTrend
                && (containsAny(priceConsistency, "choppy", "erratic") || trend.contains("volatile"))) {
            throw new IllegalArgumentException("AI stable wording contradicts volatile or choppy snapshot data");
        }

        if (combined.contains("clear trend") && !supportsClearTrendWording(trend, priceConsistency)) {
            throw new IllegalArgumentException("AI wording says clear trend without supporting snapshot data");
        }

        if (containsAny(combined, "uptrend trend", "downtrend trend")) {
            throw new IllegalArgumentException("AI trend wording repeats the trend label awkwardly");
        }
    }

    private boolean supportsClearTrendWording(String trend, String priceConsistency) {
        if (containsAny(trend, "volatile", "mixed", "unclear", "sideways")
                || containsAny(priceConsistency, "choppy", "erratic", "mixed", "unclear")) {
            return false;
        }
        return containsAny(trend, "uptrend", "downtrend", "trend")
                || containsAny(priceConsistency, "smooth", "steady", "consistent");
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

    private void validateRiskReasonableness(
            AiSuggestionContentDto content,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            List<StockAnalysisSnapshot> snapshots
    ) {
        Map<String, StockAnalysisSnapshot> snapshotBySymbol = snapshots.stream()
                .collect(Collectors.toMap(StockAnalysisSnapshot::getSymbol, Function.identity()));
        boolean saferAlternativesExist = snapshots.stream()
                .map(StockAnalysisSnapshot::getRiskCategory)
                .anyMatch(risk -> equalsIgnoreCase(risk, "conservative") || equalsIgnoreCase(risk, "moderate"));
        boolean growthAlternativesExist = snapshots.stream()
                .map(StockAnalysisSnapshot::getRiskCategory)
                .anyMatch(risk -> equalsIgnoreCase(risk, "aggressive") || equalsIgnoreCase(risk, "moderate"));
        BehaviorConfidence behaviorConfidence = behaviorConfidence(behaviorSummary);
        boolean highBehaviorConfidence = behaviorConfidence == BehaviorConfidence.HIGH;

        AiSuggestedStockDto top = content.suggestedStocks().get(0);
        StockAnalysisSnapshot topSnapshot = snapshotBySymbol.get(normalizeSymbol(top.symbol()));
        if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                && equalsIgnoreCase(topSnapshot.getRiskCategory(), "aggressive")
                && saferAlternativesExist
                && !highBehaviorConfidence) {
            throw new IllegalArgumentException("Conservative profile received aggressive top suggestion");
        }
        if (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE
                && highBehaviorConfidence
                && equalsIgnoreCase(behaviorRiskPreference(behaviorSummary), "conservative")
                && equalsIgnoreCase(topSnapshot.getRiskCategory(), "aggressive")
                && saferAlternativesExist) {
            throw new IllegalArgumentException("Aggressive profile received aggressive top suggestion despite lower-risk high-confidence behavior");
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
            if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                    && highBehaviorConfidence
                    && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")
                    && stock.matchScore() >= 80
                    && !mentionsOnboardingBehaviorConflict(stock)) {
                throw new IllegalArgumentException("High-confidence behavior conflict was not explained");
            }
            if (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE
                    && highBehaviorConfidence
                    && equalsIgnoreCase(behaviorRiskPreference(behaviorSummary), "conservative")
                    && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")
                    && stock.matchScore() >= 80
                    && !mentionsOnboardingBehaviorConflict(stock)) {
                throw new IllegalArgumentException("High-confidence lower-risk behavior conflict was not explained");
            }
        }

        boolean allConservative = content.suggestedStocks().stream()
                .map(stock -> snapshotBySymbol.get(normalizeSymbol(stock.symbol())))
                .allMatch(snapshot -> equalsIgnoreCase(snapshot.getRiskCategory(), "conservative"));
        if (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE && allConservative && growthAlternativesExist) {
            throw new IllegalArgumentException("Aggressive profile received only conservative suggestions");
        }
        boolean allAggressive = content.suggestedStocks().stream()
                .map(stock -> snapshotBySymbol.get(normalizeSymbol(stock.symbol())))
                .allMatch(snapshot -> equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive"));
        if (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE
                && highBehaviorConfidence
                && equalsIgnoreCase(behaviorRiskPreference(behaviorSummary), "conservative")
                && allAggressive
                && saferAlternativesExist) {
            throw new IllegalArgumentException("Aggressive profile received only aggressive suggestions despite lower-risk high-confidence behavior");
        }
    }

    private boolean mentionsOnboardingBehaviorConflict(AiSuggestedStockDto stock) {
        return mentionsOnboardingBehaviorConflictText(
                valueOrBlank(stock.shortReason()) + " " + valueOrBlank(stock.detailReason())
        );
    }

    private boolean mentionsOnboardingBehaviorConflictText(String value) {
        String text = valueOrBlank(value).toString().toLowerCase(Locale.ROOT);

        boolean mentionsDeclaredProfile = text.contains("onboarding")
                || text.contains("declared");

        boolean mentionsObservedBehavior = text.contains("observed")
                || text.contains("behavior")
                || text.contains("recent paper-trading")
                || text.contains("recent paper trading")
                || text.contains("recent practice")
                || text.contains("simulated trades");

        return mentionsDeclaredProfile && mentionsObservedBehavior;
    }

    private AiSuggestionContentDto buildRuleBasedFallback(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            PersonalizationWeight personalizationWeight,
            List<StockAnalysisSnapshot> snapshots,
            List<CandidateFitSignal> candidateFitSignals
    ) {
        Map<String, CandidateFitSignal> signalBySymbol = candidateFitSignals.stream()
                .collect(Collectors.toMap(CandidateFitSignal::symbol, Function.identity(), (first, second) -> first));
        List<RankedSnapshot> rankedSnapshots = snapshots.stream()
                .map(snapshot -> new RankedSnapshot(snapshot, fallbackScore(signalBySymbol.get(snapshot.getSymbol()))))
                .sorted(Comparator.comparing(RankedSnapshot::score).reversed())
                .limit(Math.min(MAX_SUGGESTIONS, snapshots.size()))
                .toList();

        List<AiSuggestedStockDto> suggestions = new ArrayList<>();
        for (int i = 0; i < rankedSnapshots.size(); i++) {
            RankedSnapshot ranked = rankedSnapshots.get(i);
            int displayScore = clampScore(ranked.score() - (i * 2));
            suggestions.add(toFallbackSuggestion(
                    profile,
                    behaviorSummary,
                    personalizationWeight,
                    signalBySymbol.get(ranked.snapshot().getSymbol()),
                    ranked.snapshot(),
                    displayScore
            ));
        }

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
                "These paper-trading suggestions are ranked using declared onboarding preferences, observed behavior confidence, and current stored stock data quality.",
                rankedSuggestions
        );
    }

    private int fallbackScore(CandidateFitSignal signal) {
        return signal == null ? 0 : signal.combinedFitScore();
    }

    private AiSuggestedStockDto toFallbackSuggestion(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            PersonalizationWeight personalizationWeight,
            CandidateFitSignal signal,
            StockAnalysisSnapshot snapshot,
            int score
    ) {
        String companyName = StockMetadata.COMPANY_MAP.getOrDefault(snapshot.getSymbol(), snapshot.getSymbol());
        String risk = snapshot.getRiskCategory() == null ? "moderate" : snapshot.getRiskCategory();
        BehaviorConfidence confidence = behaviorConfidence(behaviorSummary);
        String behaviorSentence = confidence == BehaviorConfidence.LOW
                ? "Your paper-trading behavior is still limited, so it is treated as a secondary signal."
                : "Your observed paper-trading behavior is included with " + confidence.name().toLowerCase(Locale.ROOT) + " confidence.";
        String conflictSentence = onboardingBehaviorConflict(profile, behaviorSummary, snapshot)
                ? conflictExplanation(profile, behaviorSummary)
                : "";
        String dataQuality = signal == null ? dataQuality(snapshot) : signal.dataQualityLabel();
        return new AiSuggestedStockDto(
                snapshot.getSymbol(),
                1,
                score,
                risk,
                fallbackSuggestionLabel(signal, snapshot),
                companyName + " is ranked using your onboarding preference, behavior confidence, and stored data quality.",
                companyName + " has risk category " + safeLabel(risk)
                        + ", volatility " + safeLabel(snapshot.getVolatilityLabel())
                        + ", trend " + safeLabel(snapshot.getTrend())
                        + ", price consistency " + safeLabel(snapshot.getPriceConsistency())
                        + ", volume trend " + safeLabel(snapshot.getVolumeTrend())
                        + ", and data quality " + dataQuality.toLowerCase(Locale.ROOT)
                        + ". The fallback ranking uses onboarding weight " + personalizationWeight.onboardingWeight()
                        + " and behavior weight " + personalizationWeight.behaviorWeight()
                        + " for educational paper-trading practice. " + behaviorSentence + conflictSentence
        );
    }

    private String fallbackSuggestionLabel(CandidateFitSignal signal, StockAnalysisSnapshot snapshot) {
        if (signal != null && "MATCH".equals(signal.behaviorCompatibility())) {
            return "Lower-risk behavior fit";
        }
        if ("WEAK".equals(dataQuality(snapshot)) || "PARTIAL".equals(dataQuality(snapshot))) {
            return "Data-quality-aware practice";
        }
        if (isHighVolatility(snapshot)) {
            return "Volatility comparison practice";
        }
        String consistency = valueOrBlank(snapshot.getPriceConsistency()).toString().toLowerCase(Locale.ROOT);
        if (containsAny(consistency, "choppy", "erratic")) {
            return "Choppy movement observation";
        }
        return "Risk-aligned paper-trading practice";
    }

    private String safeLabel(Object value) {
        return value == null || value.toString().isBlank() ? "not available" : value.toString();
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

        saveOrUpdateSuggestionItems(user, snapshots, content, savedBatch, List.of(), now);
        return savedBatch;
    }

    private StockAiSuggestionBatch saveOrUpdateSuccessBatch(
            AppUser user,
            UserInvestmentProfile profile,
            List<StockAnalysisSnapshot> snapshots,
            String inputHash,
            String model,
            StockAiSuggestionTriggerReason triggerReason,
            AiSuggestionContentDto content,
            OpenAiSuggestionResult openAiResult
    ) {
        LocalDateTime now = LocalDateTime.now();
        Optional<StockAiSuggestionBatch> existingSameInputHash = batchRepository
                .findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash
                );

        if (existingSameInputHash.isEmpty()) {
            return saveBatchAndItems(
                    user,
                    profile,
                    snapshots,
                    inputHash,
                    model,
                    StockAiSuggestionBatchStatus.SUCCESS,
                    triggerReason,
                    content,
                    openAiResult,
                    null
            );
        }

        StockAiSuggestionBatch existingBatch = existingSameInputHash.get();
        if (existingBatch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS) {
            log.info("AI suggestion success found reusable same-input success batch userId={} batchId={}",
                    user.getUserId(), existingBatch.getSuggestionBatchId());
            return existingBatch;
        }

        expirePreviousActiveItems(user.getUserId(), now);
        List<StockAiSuggestionItem> existingItems = itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingBatch);
        existingBatch.setUser(user);
        existingBatch.setProfile(profile);
        existingBatch.setProfileVersion(profile.getProfileVersion());
        existingBatch.setModel(model);
        existingBatch.setPromptVersion(PROMPT_VERSION);
        existingBatch.setStatus(StockAiSuggestionBatchStatus.SUCCESS);
        existingBatch.setTriggerReason(triggerReason);
        existingBatch.setInputHash(inputHash);
        existingBatch.setBatchSummary(content.batchSummary());
        existingBatch.setAnalysisTimeframe(ANALYSIS_TIMEFRAME);
        existingBatch.setPromptTokens(openAiResult == null ? null : openAiResult.promptTokens());
        existingBatch.setCompletionTokens(openAiResult == null ? null : openAiResult.completionTokens());
        existingBatch.setTotalTokens(openAiResult == null ? null : openAiResult.totalTokens());
        existingBatch.setFinishReason(openAiResult == null ? null : openAiResult.finishReason());
        existingBatch.setErrorMessage(null);
        existingBatch.setUpdatedAt(now);
        existingBatch.setExpiresAt(now.plusHours(BATCH_EXPIRY_HOURS));
        StockAiSuggestionBatch savedBatch = batchRepository.save(existingBatch);

        saveOrUpdateSuggestionItems(user, snapshots, content, savedBatch, existingItems, now);
        return savedBatch;
    }

    private StockAiSuggestionBatch saveOrUpdateRuleBasedFallbackBatch(
            AppUser user,
            UserInvestmentProfile profile,
            List<StockAnalysisSnapshot> snapshots,
            String inputHash,
            String model,
            StockAiSuggestionTriggerReason triggerReason,
            AiSuggestionContentDto content,
            String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        Optional<StockAiSuggestionBatch> existingSameInputHash = batchRepository
                .findTopByUserUserIdAndModelAndPromptVersionAndInputHashOrderByCreatedAtDesc(
                        user.getUserId(),
                        model,
                        PROMPT_VERSION,
                        inputHash
                );

        if (existingSameInputHash.isEmpty()) {
            return saveBatchAndItems(
                    user,
                    profile,
                    snapshots,
                    inputHash,
                    model,
                    StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED,
                    triggerReason,
                    content,
                    null,
                    errorMessage
            );
        }

        StockAiSuggestionBatch existingBatch = existingSameInputHash.get();
        if (existingBatch.getStatus() == StockAiSuggestionBatchStatus.SUCCESS) {
            log.info("AI suggestion fallback found reusable same-input success batch during failure userId={} batchId={}",
                    user.getUserId(), existingBatch.getSuggestionBatchId());
            return existingBatch;
        }

        expirePreviousActiveItems(user.getUserId(), now);
        List<StockAiSuggestionItem> existingItems = itemRepository.findBySuggestionBatchOrderByRankNoAsc(existingBatch);
        existingBatch.setUser(user);
        existingBatch.setProfile(profile);
        existingBatch.setProfileVersion(profile.getProfileVersion());
        existingBatch.setModel(model);
        existingBatch.setPromptVersion(PROMPT_VERSION);
        existingBatch.setStatus(StockAiSuggestionBatchStatus.FALLBACK_RULE_BASED);
        existingBatch.setTriggerReason(triggerReason);
        existingBatch.setInputHash(inputHash);
        existingBatch.setBatchSummary(content.batchSummary());
        existingBatch.setAnalysisTimeframe(ANALYSIS_TIMEFRAME);
        existingBatch.setPromptTokens(null);
        existingBatch.setCompletionTokens(null);
        existingBatch.setTotalTokens(null);
        existingBatch.setFinishReason(null);
        existingBatch.setErrorMessage(errorMessage);
        existingBatch.setUpdatedAt(now);
        existingBatch.setExpiresAt(now.plusHours(BATCH_EXPIRY_HOURS));
        StockAiSuggestionBatch savedBatch = batchRepository.save(existingBatch);

        saveOrUpdateSuggestionItems(user, snapshots, content, savedBatch, existingItems, now);
        return savedBatch;
    }

    private StockAiSuggestionBatch refreshReusableInputBatchIfNeeded(StockAiSuggestionBatch batch, LocalDateTime now) {
        boolean batchChanged = false;
        if (batch.getExpiresAt() == null || !batch.getExpiresAt().isAfter(now)) {
            batch.setExpiresAt(now.plusHours(BATCH_EXPIRY_HOURS));
            batch.setUpdatedAt(now);
            batchChanged = true;
        }

        List<StockAiSuggestionItem> allItems = itemRepository.findBySuggestionBatchOrderByRankNoAsc(batch);
        boolean itemsChanged = normalizeReusableVisibleItems(allItems, now);
        if (itemsChanged && !batchChanged) {
            batch.setUpdatedAt(now);
            batchChanged = true;
        }

        return batchChanged ? batchRepository.save(batch) : batch;
    }

    private boolean normalizeReusableVisibleItems(List<StockAiSuggestionItem> allItems, LocalDateTime now) {
        boolean changed = false;
        List<Integer> targetRanks = allItems.stream()
                .filter(item -> item.getStatus() != StockAiSuggestionItemStatus.DISMISSED)
                .map(StockAiSuggestionItem::getRankNo)
                .filter(Objects::nonNull)
                .filter(rank -> rank >= 1 && rank <= MAX_SUGGESTIONS)
                .distinct()
                .sorted()
                .limit(MAX_SUGGESTIONS)
                .toList();

        Map<Integer, StockAiSuggestionItem> visibleByRank = new LinkedHashMap<>();
        List<StockAiSuggestionItem> visibleItemsByPreference = allItems.stream()
                .filter(this::isTopItemStatus)
                .sorted(visibleItemPreferenceComparator())
                .toList();

        for (StockAiSuggestionItem item : visibleItemsByPreference) {
            Integer rank = item.getRankNo();
            if (rank == null || !targetRanks.contains(rank) || visibleByRank.containsKey(rank)) {
                expireVisibleDuplicate(item, now);
                changed = true;
                continue;
            }
            visibleByRank.put(rank, item);
        }

        List<StockAiSuggestionItem> expiredCandidates = allItems.stream()
                .filter(item -> item.getStatus() == StockAiSuggestionItemStatus.EXPIRED)
                .sorted(newestItemComparator())
                .collect(Collectors.toMap(
                        item -> item.getRankNo() == null ? Integer.MAX_VALUE : item.getRankNo(),
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(
                        StockAiSuggestionItem::getRankNo,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        for (StockAiSuggestionItem item : expiredCandidates) {
            Integer rank = item.getRankNo();
            if (rank == null || !targetRanks.contains(rank) || visibleByRank.containsKey(rank)) {
                continue;
            }
            item.setStatus(StockAiSuggestionItemStatus.ACTIVE);
            item.setUpdatedAt(now);
            itemRepository.save(item);
            visibleByRank.put(rank, item);
            changed = true;
        }
        return changed;
    }

    private void expireVisibleDuplicate(StockAiSuggestionItem item, LocalDateTime now) {
        item.setStatus(StockAiSuggestionItemStatus.EXPIRED);
        item.setUpdatedAt(now);
        itemRepository.save(item);
    }

    private Comparator<StockAiSuggestionItem> visibleItemPreferenceComparator() {
        return Comparator
                .comparingInt((StockAiSuggestionItem item) ->
                        item.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED ? 0 : 1)
                .thenComparing(newestItemComparator());
    }

    private Comparator<StockAiSuggestionItem> newestItemComparator() {
        return Comparator
                .comparing(
                        this::itemUpdatedOrCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        StockAiSuggestionItem::getSuggestionItemId,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
    }

    private LocalDateTime itemUpdatedOrCreatedAt(StockAiSuggestionItem item) {
        return item.getUpdatedAt() == null ? item.getCreatedAt() : item.getUpdatedAt();
    }

    private boolean isTopItemStatus(StockAiSuggestionItem item) {
        return item.getStatus() == StockAiSuggestionItemStatus.ACTIVE
                || item.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED;
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
        Map<String, DelayedMarketPrice> delayedPriceBySymbol = loadDelayedPrices(symbols);

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
                        .map(item -> toSuggestedResponse(
                                item,
                                stockBySymbol.get(item.getSymbol()),
                                watchlistedSymbols.contains(item.getSymbol()),
                                delayedPriceBySymbol.get(item.getSymbol())
                        ))
                        .toList(),
                buildRemainingStocks(topSymbols, stockBySymbol, watchlistedSymbols, delayedPriceBySymbol),
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
        Map<String, DelayedMarketPrice> delayedPriceBySymbol = loadDelayedPrices(symbols);

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
                buildRemainingStocks(Set.of(), stockBySymbol, watchlistedSymbols, delayedPriceBySymbol),
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

    private SuggestedStockResponse toSuggestedResponse(
            StockAiSuggestionItem item,
            Stock stock,
            boolean isWatchlisted,
            DelayedMarketPrice delayedPrice
    ) {
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
                isWatchlisted || item.getStatus() == StockAiSuggestionItemStatus.WATCHLISTED,
                delayedPrice == null ? null : delayedPrice.displayedPrice(),
                delayedPrice == null ? null : delayedPrice.displayedAbsoluteChange(),
                delayedPrice == null ? null : delayedPrice.displayedPercentChange(),
                delayedPrice == null ? null : delayedPrice.previousClose(),
                delayedPrice == null ? null : delayedPrice.priceFreshnessStatusName(),
                delayedPrice == null ? null : delayedPrice.priceFreshnessLabel(),
                DelayedPriceMetadataResponse.from(delayedPrice),
                delayedPrice == null ? null : delayedPrice.priceSource(),
                delayedPrice == null ? null : delayedPrice.displayedMarketTime()
        );
    }

    private List<RemainingStockResponse> buildRemainingStocks(
            Set<String> excludedSymbols,
            Map<String, Stock> stockBySymbol,
            Set<String> watchlistedSymbols,
            Map<String, DelayedMarketPrice> delayedPriceBySymbol
    ) {
        return SUPPORTED_SYMBOLS.stream()
                .filter(symbol -> !excludedSymbols.contains(symbol))
                .map(symbol -> {
                    Stock stock = stockBySymbol.get(symbol);
                    DelayedMarketPrice delayedPrice = delayedPriceBySymbol.get(symbol);
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
                            watchlistedSymbols.contains(symbol),
                            delayedPrice == null ? null : delayedPrice.displayedPrice(),
                            delayedPrice == null ? null : delayedPrice.displayedAbsoluteChange(),
                            delayedPrice == null ? null : delayedPrice.displayedPercentChange(),
                            delayedPrice == null ? null : delayedPrice.previousClose(),
                            delayedPrice == null ? null : delayedPrice.priceFreshnessStatusName(),
                            delayedPrice == null ? null : delayedPrice.priceFreshnessLabel(),
                            DelayedPriceMetadataResponse.from(delayedPrice),
                            delayedPrice == null ? null : delayedPrice.priceSource(),
                            delayedPrice == null ? null : delayedPrice.displayedMarketTime()
                    );
                })
                .toList();
    }

    private Map<String, DelayedMarketPrice> loadDelayedPrices(Collection<String> symbols) {
        Map<String, DelayedMarketPrice> delayedPriceBySymbol = new LinkedHashMap<>();
        for (String symbol : symbols) {
            try {
                delayedPriceBySymbol.put(symbol, delayedMarketPriceService.resolveForDisplay(symbol));
            } catch (RuntimeException exception) {
                log.warn("Delayed display data unavailable for AI suggestion symbol={}", symbol);
                delayedPriceBySymbol.put(symbol, null);
            }
        }
        return delayedPriceBySymbol;
    }

    private String buildPromptInput(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            PersonalizationWeight personalizationWeight,
            List<StockAnalysisSnapshot> snapshots,
            List<CandidateFitSignal> candidateFitSignals,
            String validationError
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("declaredOnboardingProfile", declaredOnboardingProfile(profile));
        input.put("observedPaperTradingBehavior", observedPaperTradingBehavior(behaviorSummary));
        input.put("personalizationWeight", personalizationWeightToMap(personalizationWeight));
        input.put("analysisTimeframe", ANALYSIS_TIMEFRAME);
        input.put("maxSuggestions", Math.min(MAX_SUGGESTIONS, snapshots.size()));
        input.put("supportedStockUniverse", Map.of(
                "version", SUPPORTED_SYMBOLS_VERSION,
                "symbols", SUPPORTED_SYMBOLS
        ));
        input.put("beginnerSafetyRules", List.of(
                "Suggestions are for educational paper-trading practice only.",
                "Do not provide real-money investment advice.",
                "Do not predict future prices or guaranteed returns.",
                "Do not use unsafe phrases such as must buy, sure win, safe profit, or risk-free return.",
                "Explain declared onboarding preference and observed paper-trading behavior separately when both are relevant."
        ));
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
            input.put("retryInstruction", retryInstruction(validationError));
            input.put("retryConstraints", retryConstraints(validationError));
        }

        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build suggestion prompt input", e);
        }
    }

    private String retryInstruction(String validationError) {
        return "Fix the validation issue and return only valid JSON. Keep all wording educational, paper-trading focused, and directly supported by the provided snapshot fields.";
    }

    private List<String> retryConstraints(String validationError) {
        List<String> constraints = new ArrayList<>();
        String error = validationError == null ? "" : validationError.toLowerCase(Locale.ROOT);
        if (error.contains("volatile or choppy") || error.contains("stable wording") || error.contains("clear trend")) {
            constraints.add("Do not use steady, smooth, consistent, stable, or clear trend wording for any stock whose trend contains volatile or whose priceConsistency contains choppy or erratic.");
            constraints.add("If a stock has choppy or erratic priceConsistency, say choppy or erratic directly instead of using positive movement wording.");
        }
        if (error.contains("detail reason") || error.contains("input factors")) {
            constraints.add("Each detailReason must mention at least two concrete provided factors using natural wording, such as risk, volatility, trend, price movement, volume, behavior confidence, or data quality.");
            constraints.add("Aim for 40 to 70 words, but keep the explanation natural and beginner-friendly.");
        }
        if (error.contains("trend wording") || error.contains("trend label")) {
            constraints.add("Do not write repeated trend wording such as uptrend trend or downtrend trend; use trend is or trend label is instead.");
        }
        constraints.add("Use safe labels such as Risk-aligned paper-trading practice, Choppy movement observation, Volatility comparison practice, Data-quality-aware practice, or Lower-risk behavior fit.");
        constraints.add("Avoid awkward repeated trend wording such as uptrend trend or downtrend trend; write trend is or trend label is.");
        constraints.add("Do not expose backend field names such as riskCategory, volatilityLabel, priceConsistency, volumeTrend, dataQualityLabel, behaviorCompatibility, behaviorFitScore, combinedFitScore, MATCH, PARTIAL_MATCH, COMPLETE, or behaviorConfidence HIGH. Convert them into natural beginner wording.");
        constraints.add("Do not use real-money advice, guaranteed return wording, future price predictions, or external news.");
        return constraints;
    }

    private Map<String, Object> declaredOnboardingProfile(UserInvestmentProfile profile) {
        Map<String, Object> declared = new LinkedHashMap<>();
        declared.put("profileVersion", profile.getProfileVersion());
        declared.put("profileSource", profile.getProfileSource());
        declared.put("riskTolerance", profile.getRiskTolerance());
        declared.put("investmentGoal", profile.getInvestmentGoal());
        declared.put("experienceLevel", profile.getExperienceLevel());
        declared.put("preferredVolatility", profile.getPreferredVolatility());
        declared.put("preferredHorizon", profile.getPreferredHorizon());
        declared.put("riskScore", valueOrBlank(profile.getRiskScore()));
        declared.put("goalScore", valueOrBlank(profile.getGoalScore()));
        declared.put("experienceScore", valueOrBlank(profile.getExperienceScore()));
        declared.put("meaning", "Declared preference from onboarding quiz. Do not treat this as observed trading behavior.");
        return declared;
    }

    private Map<String, Object> observedPaperTradingBehavior(BehaviorSummaryForSuggestion behaviorSummary) {
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("hasBehaviorProfile", behaviorSummary.hasProfile());
        observed.put("behaviorRiskScore", valueOrBlank(behaviorSummary.behaviorRiskScore()));
        observed.put("behaviorStyle", valueOrBlank(behaviorSummary.behaviorStyle()));
        observed.put("behaviorConfidence", behaviorConfidence(behaviorSummary));
        observed.put("averagePositionSizePercent", valueOrBlank(behaviorSummary.averagePositionSizePercent()));
        observed.put("turnoverLevel", valueOrBlank(behaviorSummary.turnoverLevel()));
        observed.put("concentrationLevel", valueOrBlank(behaviorSummary.concentrationLevel()));
        observed.put("highVolatilityExposure", valueOrBlank(behaviorSummary.highVolatilityExposure()));
        observed.put("stockRiskExposureScore", valueOrBlank(behaviorSummary.stockRiskExposureScore()));
        observed.put("concentrationScore", valueOrBlank(behaviorSummary.concentrationScore()));
        observed.put("turnoverScore", valueOrBlank(behaviorSummary.turnoverScore()));
        observed.put("holdingPeriodScore", valueOrBlank(behaviorSummary.holdingPeriodScore()));
        observed.put("volatilityExposureScore", valueOrBlank(behaviorSummary.volatilityExposureScore()));
        observed.put("favoriteRiskCategory", valueOrBlank(behaviorSummary.favoriteRiskCategory()));
        observed.put("mostTradedSymbols", valueOrBlank(behaviorSummary.mostTradedSymbols()));
        observed.put("meaning", "Observed behavior from simulated paper trading only. LOW confidence is informational.");
        return observed;
    }

    private Map<String, Object> personalizationWeightToMap(PersonalizationWeight personalizationWeight) {
        Map<String, Object> weight = new LinkedHashMap<>();
        weight.put("onboardingWeight", personalizationWeight.onboardingWeight());
        weight.put("behaviorWeight", personalizationWeight.behaviorWeight());
        weight.put("explanation", personalizationWeight.explanation());
        return weight;
    }

    private List<CandidateFitSignal> buildCandidateFitSignals(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            PersonalizationWeight personalizationWeight,
            List<StockAnalysisSnapshot> snapshots
    ) {
        return snapshots.stream()
                .sorted(Comparator.comparing(StockAnalysisSnapshot::getSymbol))
                .map(snapshot -> {
                    String riskCompatibility = riskCompatibility(profile, snapshot);
                    String behaviorCompatibility = behaviorCompatibility(profile, behaviorSummary, snapshot);
                    String dataQualityLabel = dataQuality(snapshot);
                    int dataQualityPenalty = dataQualityPenalty(dataQualityLabel);
                    int dataQualityScore = dataQualityScore(dataQualityLabel);
                    int onboardingFitScore = onboardingFitScore(profile, snapshot);
                    int behaviorFitScore = behaviorFitScore(behaviorSummary, behaviorCompatibility, snapshot);
                    int trendScore = trendScore(snapshot);
                    int riskPenalty = riskPenalty(profile, behaviorSummary, snapshot);
                    int combinedFitScore = combinedFitScore(
                            onboardingFitScore,
                            behaviorFitScore,
                            dataQualityScore,
                            trendScore,
                            riskPenalty,
                            personalizationWeight
                    );
                    List<String> warningSignals = warningSignals(profile, behaviorSummary, snapshot, dataQualityLabel);

                    return new CandidateFitSignal(
                            snapshot.getSymbol(),
                            StockMetadata.COMPANY_MAP.getOrDefault(snapshot.getSymbol(), snapshot.getSymbol()),
                            valueOrBlank(snapshot.getRiskCategory()).toString(),
                            valueOrBlank(snapshot.getTrend()).toString(),
                            valueOrBlank(snapshot.getVolatilityLabel()).toString(),
                            valueOrBlank(snapshot.getVolumeTrend()).toString(),
                            riskCompatibility,
                            behaviorCompatibility,
                            dataQualityLabel,
                            dataQualityPenalty,
                            dataQualityScore,
                            trendScore,
                            onboardingFitScore,
                            behaviorFitScore,
                            combinedFitScore,
                            candidateFitNotes(riskCompatibility, behaviorSummary, personalizationWeight, dataQualityLabel),
                            warningSignals,
                            valueOrBlank(snapshot.getSnapshotHash()).toString()
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
        if (confidence == BehaviorConfidence.LOW || !hasBehaviorRiskSignals(behaviorSummary)) {
            return "INSUFFICIENT_DATA";
        }
        if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                && confidence != BehaviorConfidence.HIGH
                && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")) {
            return "MISMATCH";
        }

        String aligned = behaviorRiskAlignment(behaviorSummary, snapshot);
        if (confidence == BehaviorConfidence.MEDIUM && "MATCH".equals(aligned)) {
            return "PARTIAL_MATCH";
        }
        return aligned;
    }

    private String behaviorRiskAlignment(BehaviorSummaryForSuggestion behaviorSummary, StockAnalysisSnapshot snapshot) {
        return riskPreferenceAlignment(behaviorRiskPreference(behaviorSummary), snapshot);
    }

    private String behaviorRiskPreference(BehaviorSummaryForSuggestion behaviorSummary) {
        if (!isBlank(behaviorSummary.favoriteRiskCategory())) {
            String favoriteRiskCategory = normalizeRisk(behaviorSummary.favoriteRiskCategory());
            if (isKnownRiskPreference(favoriteRiskCategory)) {
                return favoriteRiskCategory;
            }
        }
        String scoreRiskPreference = scoreRiskPreference(behaviorSummary.stockRiskExposureScore());
        if (scoreRiskPreference != null) {
            return scoreRiskPreference;
        }
        scoreRiskPreference = scoreRiskPreference(behaviorSummary.behaviorRiskScore());
        if (scoreRiskPreference != null) {
            return scoreRiskPreference;
        }
        if (behaviorSummary.highVolatilityExposure() != null) {
            return switch (behaviorSummary.highVolatilityExposure()) {
                case LOW -> "conservative";
                case MEDIUM -> "moderate";
                case HIGH -> "aggressive";
            };
        }
        scoreRiskPreference = scoreRiskPreference(behaviorSummary.volatilityExposureScore());
        if (scoreRiskPreference != null) {
            return scoreRiskPreference;
        }
        return behaviorStyleRiskPreference(behaviorSummary.behaviorStyle());
    }

    private boolean hasBehaviorRiskSignals(BehaviorSummaryForSuggestion behaviorSummary) {
        return !isBlank(behaviorSummary.favoriteRiskCategory())
                || behaviorSummary.stockRiskExposureScore() != null
                || behaviorSummary.behaviorRiskScore() != null
                || behaviorSummary.highVolatilityExposure() != null
                || behaviorSummary.volatilityExposureScore() != null
                || behaviorSummary.behaviorStyle() != null;
    }

    private String behaviorStyleRiskPreference(UserBehaviorStyle behaviorStyle) {
        if (behaviorStyle == null) {
            return "moderate";
        }
        return switch (behaviorStyle) {
            case CONSERVATIVE -> "conservative";
            case BALANCED -> "moderate";
            case AGGRESSIVE -> "aggressive";
            case ACTIVE_TRADER, INSUFFICIENT_DATA -> "moderate";
        };
    }

    private String scoreRiskPreference(Integer score) {
        if (score == null) {
            return null;
        }
        if (score <= 40) {
            return "conservative";
        }
        if (score >= 70) {
            return "aggressive";
        }
        return "moderate";
    }

    private boolean isKnownRiskPreference(String riskPreference) {
        return "conservative".equals(riskPreference)
                || "moderate".equals(riskPreference)
                || "aggressive".equals(riskPreference);
    }

    private String riskPreferenceAlignment(String riskPreference, StockAnalysisSnapshot snapshot) {
        String riskCategory = normalizeRisk(snapshot.getRiskCategory());
        return switch (riskPreference) {
            case "conservative" -> {
                if ("conservative".equals(riskCategory)) {
                    yield "MATCH";
                }
                if ("moderate".equals(riskCategory)) {
                    yield "PARTIAL_MATCH";
                }
                yield "MISMATCH";
            }
            case "aggressive" -> {
                if ("aggressive".equals(riskCategory)) {
                    yield "MATCH";
                }
                if ("moderate".equals(riskCategory)) {
                    yield "PARTIAL_MATCH";
                }
                yield "MISMATCH";
            }
            default -> "moderate".equals(riskCategory) ? "MATCH" : "PARTIAL_MATCH";
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

    private int dataQualityScore(String dataQuality) {
        return switch (dataQuality) {
            case "WEAK" -> 55;
            case "PARTIAL" -> 80;
            default -> 100;
        };
    }

    private int onboardingFitScore(UserInvestmentProfile profile, StockAnalysisSnapshot snapshot) {
        String risk = normalizeRisk(snapshot.getRiskCategory());
        RiskTolerance riskTolerance = profile.getRiskTolerance() == null ? RiskTolerance.MODERATE : profile.getRiskTolerance();
        int score = switch (riskTolerance) {
            case CONSERVATIVE -> switch (risk) {
                case "conservative" -> 92;
                case "moderate" -> 78;
                default -> 42;
            };
            case AGGRESSIVE -> switch (risk) {
                case "aggressive" -> 90;
                case "moderate_aggressive" -> 84;
                case "moderate" -> 76;
                default -> 56;
            };
            default -> switch (risk) {
                case "moderate" -> 90;
                case "conservative", "moderate_aggressive" -> 76;
                default -> 62;
            };
        };

        if (profile.getPreferredVolatility() == PreferredVolatility.LOW && isHighVolatility(snapshot)) {
            score -= 15;
        }
        if (profile.getPreferredVolatility() == PreferredVolatility.HIGH && isHighVolatility(snapshot)) {
            score += 6;
        }
        return clampScore(score);
    }

    private int behaviorFitScore(
            BehaviorSummaryForSuggestion behaviorSummary,
            String behaviorCompatibility,
            StockAnalysisSnapshot snapshot
    ) {
        BehaviorConfidence confidence = behaviorConfidence(behaviorSummary);
        if (confidence == BehaviorConfidence.LOW || "INSUFFICIENT_DATA".equals(behaviorCompatibility)) {
            return 50;
        }

        int score = switch (behaviorCompatibility) {
            case "MATCH" -> 90;
            case "PARTIAL_MATCH" -> 72;
            case "MISMATCH" -> 36;
            default -> 50;
        };

        if (behaviorSummary.favoriteRiskCategory() != null
                && equalsIgnoreCase(normalizeRisk(behaviorSummary.favoriteRiskCategory()), normalizeRisk(snapshot.getRiskCategory()))) {
            score += confidence == BehaviorConfidence.HIGH ? 10 : 6;
        }
        if (containsSymbol(behaviorSummary.mostTradedSymbols(), snapshot.getSymbol())) {
            score += confidence == BehaviorConfidence.HIGH ? 8 : 4;
        }
        if (behaviorSummary.highVolatilityExposure() != null && isHighVolatility(snapshot)) {
            score += switch (behaviorSummary.highVolatilityExposure()) {
                case HIGH -> 8;
                case MEDIUM -> 3;
                case LOW -> -8;
            };
        }
        return clampScore(score);
    }

    private int trendScore(StockAnalysisSnapshot snapshot) {
        String trend = valueOrBlank(snapshot.getTrend()).toString().toLowerCase(Locale.ROOT);
        String consistency = valueOrBlank(snapshot.getPriceConsistency()).toString().toLowerCase(Locale.ROOT);
        int score = 65;
        if (trend.contains("uptrend") || trend.contains("upward")) {
            score += 18;
        }
        if (trend.contains("sideways")) {
            score -= 5;
        }
        if (trend.contains("downtrend") || trend.contains("downward")) {
            score -= 12;
        }
        if (consistency.contains("smooth") || consistency.contains("steady")) {
            score += 8;
        }
        if (consistency.contains("choppy")) {
            score -= 10;
        }
        if (consistency.contains("erratic")) {
            score -= 18;
        }
        return clampScore(score);
    }

    private int riskPenalty(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            StockAnalysisSnapshot snapshot
    ) {
        BehaviorConfidence confidence = behaviorConfidence(behaviorSummary);
        if (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")
                && confidence != BehaviorConfidence.HIGH) {
            return confidence == BehaviorConfidence.MEDIUM ? 15 : 30;
        }
        if (profile.getRiskTolerance() == RiskTolerance.MODERATE
                && profile.getExperienceLevel() != null
                && profile.getExperienceLevel().name().equals("BEGINNER")
                && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive")
                && confidence != BehaviorConfidence.HIGH) {
            return 18;
        }
        return 0;
    }

    private int combinedFitScore(
            int onboardingFitScore,
            int behaviorFitScore,
            int dataQualityScore,
            int trendScore,
            int riskPenalty,
            PersonalizationWeight personalizationWeight
    ) {
        int personalizationScore = Math.round((
                onboardingFitScore * personalizationWeight.onboardingWeight()
                        + behaviorFitScore * personalizationWeight.behaviorWeight()
        ) / 100.0f);
        int combined = Math.round(personalizationScore * 0.70f + dataQualityScore * 0.20f + trendScore * 0.10f) - riskPenalty;
        return clampScore(combined);
    }

    private List<String> warningSignals(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            StockAnalysisSnapshot snapshot,
            String dataQuality
    ) {
        List<String> warnings = new ArrayList<>();
        if ("WEAK".equals(dataQuality)) {
            warnings.add("Recent data has notable gaps");
        } else if ("PARTIAL".equals(dataQuality)) {
            warnings.add("Recent data is partially complete");
        }
        if (onboardingBehaviorConflict(profile, behaviorSummary, snapshot)) {
            warnings.add("Observed behavior conflicts with declared onboarding preference");
        }
        if (profile.getPreferredVolatility() == PreferredVolatility.LOW && isHighVolatility(snapshot)) {
            warnings.add("Volatility is higher than declared preference");
        }
        return warnings;
    }

    private List<String> candidateFitNotes(
            String riskCompatibility,
            BehaviorSummaryForSuggestion behaviorSummary,
            PersonalizationWeight personalizationWeight,
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
        notes.add("Personalization weight is onboarding " + personalizationWeight.onboardingWeight()
                + " and behavior " + personalizationWeight.behaviorWeight());
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
        map.put("companyName", signal.companyName());
        map.put("riskCategory", signal.riskCategory());
        map.put("trendLabel", signal.trendLabel());
        map.put("volatilityLabel", signal.volatilityLabel());
        map.put("volumeTrendLabel", signal.volumeTrendLabel());
        map.put("dataQualityLabel", signal.dataQualityLabel());
        map.put("riskCompatibility", signal.riskCompatibility());
        map.put("behaviorCompatibility", signal.behaviorCompatibility());
        map.put("onboardingFitScore", signal.onboardingFitScore());
        map.put("behaviorFitScore", signal.behaviorFitScore());
        map.put("combinedFitScore", signal.combinedFitScore());
        map.put("combinedFitScoreMeaning", "Backend educational fit score. Use as the main ranking anchor, but still explain using stockSnapshots.");
        map.put("dataQualityScore", signal.dataQualityScore());
        map.put("trendScore", signal.trendScore());
        map.put("dataQualityPenalty", signal.dataQualityPenalty());
        map.put("fitNotes", signal.fitNotes());
        map.put("warningSignals", signal.warningSignals());
        map.put("snapshotHash", signal.snapshotHash());
        return map;
    }

    private List<String> candidateFitSignals(List<CandidateFitSignal> signals) {
        return signals.stream()
                .sorted(Comparator.comparing(CandidateFitSignal::symbol))
                .map(signal -> signal.symbol()
                        + ":risk=" + signal.riskCompatibility()
                        + ",behavior=" + signal.behaviorCompatibility()
                        + ",score=" + signal.combinedFitScore()
                        + ",data=" + signal.dataQualityLabel())
                .toList();
    }

    private PersonalizationWeight personalizationWeight(BehaviorSummaryForSuggestion behaviorSummary) {
        return switch (behaviorConfidence(behaviorSummary)) {
            case HIGH -> new PersonalizationWeight(
                    10,
                    90,
                    "Observed paper-trading behavior is strong enough to become the primary personalization signal."
            );
            case MEDIUM -> new PersonalizationWeight(
                    40,
                    60,
                    "Observed paper-trading behavior is now meaningful and can adjust the declared onboarding preferences."
            );
            case LOW -> new PersonalizationWeight(
                    80,
                    20,
                    "Observed paper-trading behavior is still limited, so declared onboarding preferences remain the primary signal."
            );
        };
    }

    private BehaviorSummaryForSuggestion behaviorSummaryOrLowNoData(BehaviorSummaryForSuggestion behaviorSummary) {
        if (behaviorSummary != null) {
            return behaviorSummary;
        }
        return new BehaviorSummaryForSuggestion(
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
        );
    }

    private BehaviorConfidence behaviorConfidence(BehaviorSummaryForSuggestion behaviorSummary) {
        return behaviorSummary == null || behaviorSummary.behaviorConfidence() == null
                ? BehaviorConfidence.LOW
                : behaviorSummary.behaviorConfidence();
    }

    private boolean onboardingBehaviorConflict(
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            StockAnalysisSnapshot snapshot
    ) {
        BehaviorConfidence confidence = behaviorConfidence(behaviorSummary);
        if (confidence != BehaviorConfidence.HIGH) {
            return false;
        }
        String behaviorRiskPreference = behaviorRiskPreference(behaviorSummary);
        return (profile.getRiskTolerance() == RiskTolerance.CONSERVATIVE
                && "aggressive".equals(behaviorRiskPreference)
                && equalsIgnoreCase(snapshot.getRiskCategory(), "aggressive"))
                || (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE
                && "conservative".equals(behaviorRiskPreference)
                && equalsIgnoreCase(snapshot.getRiskCategory(), "conservative"));
    }

    private String conflictExplanation(UserInvestmentProfile profile, BehaviorSummaryForSuggestion behaviorSummary) {
        if (profile.getRiskTolerance() == RiskTolerance.AGGRESSIVE
                && equalsIgnoreCase(behaviorRiskPreference(behaviorSummary), "conservative")) {
            return " Although your onboarding profile was growth-focused and aggressive, your recent paper-trading behavior shows a preference for lower-risk symbols, so this is presented only as an educational paper-trading example.";
        }
        return " Although your onboarding profile was conservative, your paper-trading behavior shows higher risk tolerance, so this is presented only as an educational paper-trading example.";
    }

    private String normalizeRisk(String value) {
        if (value == null) {
            return "moderate";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private boolean isHighVolatility(StockAnalysisSnapshot snapshot) {
        String volatility = valueOrBlank(snapshot.getVolatilityLabel()).toString().toLowerCase(Locale.ROOT);
        return volatility.contains("high");
    }

    private boolean containsSymbol(String symbols, String symbol) {
        if (symbols == null || symbol == null) {
            return false;
        }
        String normalized = normalizeSymbol(symbol);
        return Arrays.stream(symbols.split(","))
                .map(this::normalizeSymbol)
                .anyMatch(normalized::equals);
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        return Arrays.stream(needles).anyMatch(value::contains);
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private Object valueOrBlank(Object value) {
        return value == null ? "" : value;
    }

    private String hashInput(
            AppUser user,
            UserInvestmentProfile profile,
            BehaviorSummaryForSuggestion behaviorSummary,
            PersonalizationWeight personalizationWeight,
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
                        signal.companyName(),
                        signal.riskCategory(),
                        signal.trendLabel(),
                        signal.volatilityLabel(),
                        signal.volumeTrendLabel(),
                        signal.riskCompatibility(),
                        signal.behaviorCompatibility(),
                        signal.dataQualityLabel(),
                        String.valueOf(signal.dataQualityPenalty()),
                        String.valueOf(signal.dataQualityScore()),
                        String.valueOf(signal.trendScore()),
                        String.valueOf(signal.onboardingFitScore()),
                        String.valueOf(signal.behaviorFitScore()),
                        String.valueOf(signal.combinedFitScore()),
                        String.join(",", signal.warningSignals()),
                        signal.snapshotHash(),
                        String.join(",", signal.fitNotes())
                ))
                .collect(Collectors.joining("|"));
        String raw = String.join("|",
                String.valueOf(user.getUserId()),
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
                String.valueOf(personalizationWeight.onboardingWeight()),
                String.valueOf(personalizationWeight.behaviorWeight()),
                String.valueOf(personalizationWeight.explanation()),
                String.valueOf(behaviorSummary.behaviorRiskScore()),
                String.valueOf(behaviorSummary.behaviorStyle()),
                String.valueOf(behaviorConfidence(behaviorSummary)),
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
                candidateFitPart,
                snapshotHashPart,
                model,
                PROMPT_VERSION,
                ANALYSIS_TIMEFRAME,
                SUPPORTED_SYMBOLS_VERSION
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
            String companyName,
            String riskCategory,
            String trendLabel,
            String volatilityLabel,
            String volumeTrendLabel,
            String riskCompatibility,
            String behaviorCompatibility,
            String dataQualityLabel,
            int dataQualityPenalty,
            int dataQualityScore,
            int trendScore,
            int onboardingFitScore,
            int behaviorFitScore,
            int combinedFitScore,
            List<String> fitNotes,
            List<String> warningSignals,
            String snapshotHash
    ) {
    }

    private record PersonalizationWeight(
            int onboardingWeight,
            int behaviorWeight,
            String explanation
    ) {
    }

    private record RefreshCooldown(boolean refreshAllowed, LocalDateTime nextRefreshAllowedAt) {
    }
}
