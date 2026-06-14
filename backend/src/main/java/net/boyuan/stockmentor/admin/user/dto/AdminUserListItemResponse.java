package net.boyuan.stockmentor.admin.user.dto;

import java.time.LocalDateTime;

public record AdminUserListItemResponse(
        Long userId,
        String email,
        String username,
        String role,
        String status,
        Boolean isDeleted,
        Boolean onboardingCompleted,
        Boolean hasInvestmentProfile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {
}
