package net.boyuan.stockmentor.auth.service;

import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository appUserRepository;

    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authenticated user is required");
        }

        String principal = authentication.getName();
        return appUserRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(principal, principal)
                .filter(user -> user.getStatus() == AppUserStatus.ACTIVE)
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not active"));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }
}
