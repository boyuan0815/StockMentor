package net.boyuan.stockmentor.userbehavior.repository;

import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBehaviorProfileRepository extends JpaRepository<UserBehaviorProfile, Long> {
    Optional<UserBehaviorProfile> findTopByUserUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<UserBehaviorProfile> findTopByUserUserIdAndBehaviorConfidenceAndBehaviorStyleOrderByUpdatedAtDesc(
            Long userId,
            BehaviorConfidence behaviorConfidence,
            UserBehaviorStyle behaviorStyle
    );
}
