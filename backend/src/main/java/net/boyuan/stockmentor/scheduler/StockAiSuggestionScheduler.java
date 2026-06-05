package net.boyuan.stockmentor.scheduler;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.StockAiSuggestionResponse;
import net.boyuan.stockmentor.ai.model.StockAiSuggestionTriggerReason;
import net.boyuan.stockmentor.ai.service.StockAiSuggestionService;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.common.util.MarketTimeService;
import net.boyuan.stockmentor.userprofile.repository.UserInvestmentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockAiSuggestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StockAiSuggestionScheduler.class);
    private static final int PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final StockAiSuggestionService stockAiSuggestionService;
    private final MarketTimeService marketTimeService;

    @Scheduled(cron = "0 30 19 * * MON-FRI", zone = "America/New_York")
    void refreshSuggestionsAfterMarketClose() {
        if (!marketTimeService.isTradingDay()) {
            log.info("Scheduled AI suggestion refresh skipped because today is not a trading day");
            return;
        }

        ScheduledRefreshSummary summary = runScheduledRefreshForEligibleUsers();
        log.info("Scheduled AI suggestion refresh summary processed={} generated={} reusedOrSkippedUnchanged={} skippedMissingProfile={} skippedNoData={} fallback={} failed={}",
                summary.processedUsers(),
                summary.generated(),
                summary.reusedOrSkippedUnchanged(),
                summary.skippedMissingProfile(),
                summary.skippedNoData(),
                summary.fallback(),
                summary.failed());
    }

    public ScheduledRefreshSummary runScheduledRefreshForEligibleUsers() {
        int pageNumber = 0;
        int processedUsers = 0;
        int generated = 0;
        int reusedOrSkippedUnchanged = 0;
        int skippedMissingProfile = 0;
        int skippedNoData = 0;
        int fallback = 0;
        int failed = 0;
        Page<AppUser> page;

        do {
            page = appUserRepository.findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(
                    AppUserStatus.ACTIVE,
                    PageRequest.of(pageNumber, PAGE_SIZE)
            );

            for (AppUser user : page.getContent()) {
                processedUsers++;
                try {
                    if (profileRepository.findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(user.getUserId()).isEmpty()) {
                        skippedMissingProfile++;
                        log.info("Scheduled AI suggestion refresh skipped userId={} because no investment profile exists", user.getUserId());
                        continue;
                    }

                    StockAiSuggestionResponse response = stockAiSuggestionService.generateSuggestionsForUser(
                            user,
                            StockAiSuggestionTriggerReason.SCHEDULED_REFRESH,
                            false
                    );

                    if (response.batchId() == null) {
                        skippedNoData++;
                    } else if (response.message() != null && response.message().contains("unchanged")) {
                        reusedOrSkippedUnchanged++;
                    } else if (Boolean.TRUE.equals(response.fallbackUsed())) {
                        fallback++;
                    } else {
                        generated++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Scheduled AI suggestion refresh failed for userId={}", user.getUserId(), e);
                }
            }

            pageNumber++;
        } while (page.hasNext());

        return new ScheduledRefreshSummary(
                processedUsers,
                generated,
                reusedOrSkippedUnchanged,
                skippedMissingProfile,
                skippedNoData,
                fallback,
                failed
        );
    }

    public record ScheduledRefreshSummary(
            int processedUsers,
            int generated,
            int reusedOrSkippedUnchanged,
            int skippedMissingProfile,
            int skippedNoData,
            int fallback,
            int failed
    ) {
    }
}
