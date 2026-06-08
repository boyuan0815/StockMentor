package net.boyuan.stockmentor.userprofile.repository;

import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserInvestmentProfileRepository extends JpaRepository<UserInvestmentProfile, Long> {
    Optional<UserInvestmentProfile> findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(Long userId);
}
