package net.boyuan.stockmentor.auth.repository;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailOrUsername(String email, String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<AppUser> findByUserIdAndStatusAndIsDeletedFalse(Long userId, AppUserStatus status);

    Page<AppUser> findByStatusAndIsDeletedFalseAndOnboardingCompletedTrue(AppUserStatus status, Pageable pageable);
}
