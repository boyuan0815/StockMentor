package net.boyuan.stockmentor.auth.dto;

import java.time.LocalDateTime;

public record AuthUserResponse(
        Long userId,
        String email,
        String username,
        String role,
        String status,
        Boolean onboardingCompleted,
        Boolean hasInvestmentProfile,
        Boolean mustCompleteOnboarding,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
}
