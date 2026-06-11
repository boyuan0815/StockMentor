package net.boyuan.stockmentor.userprofile.repository;

import net.boyuan.stockmentor.userprofile.entity.UserInvestmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserInvestmentProfileRepository extends JpaRepository<UserInvestmentProfile, Long> {
    Optional<UserInvestmentProfile> findTopByUserUserIdOrderByProfileVersionDescUpdatedAtDesc(Long userId);

    @Query("select coalesce(max(p.profileVersion), 0) from UserInvestmentProfile p where p.user.userId = :userId")
    int findMaxProfileVersionByUserId(@Param("userId") Long userId);
}
