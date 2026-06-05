package net.boyuan.stockmentor.userbehavior.service.impl;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userbehavior.service.UserBehaviorProfileService;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserBehaviorProfileServiceImpl implements UserBehaviorProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserBehaviorProfileServiceImpl.class);
    private static final String LOW_CONFIDENCE_NOTE = "No paper-trading transaction source exists yet; behavior is LOW confidence and informational only.";

    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final AppUserRepository appUserRepository;

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
        log.info("No paper-trading transaction source exists; returning LOW confidence behavior profile for userId={}", userId);
        return createLowConfidenceProfileIfMissing(user);
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
                profile.getUpdatedAt(),
                LOW_CONFIDENCE_NOTE
        );
    }
}
