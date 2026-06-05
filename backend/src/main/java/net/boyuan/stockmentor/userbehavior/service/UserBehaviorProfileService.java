package net.boyuan.stockmentor.userbehavior.service;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.userbehavior.dto.BehaviorSummaryForSuggestion;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;

import java.util.Optional;

public interface UserBehaviorProfileService {
    Optional<UserBehaviorProfile> getLatestBehaviorProfile(Long userId);

    UserBehaviorProfile createLowConfidenceProfileIfMissing(AppUser user);

    UserBehaviorProfile recalculateBehaviorProfile(Long userId);

    BehaviorSummaryForSuggestion getBehaviorSummaryForSuggestion(Long userId);
}
