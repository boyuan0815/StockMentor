package net.boyuan.stockmentor.userprofile.dto;

public record UserProfileResponse(
        Long userId,
        String email,
        String username,
        String role,
        Boolean onboardingCompleted,
        InvestmentProfileResponse investmentProfile,
        BehaviorProfileSummaryResponse behaviorSummary
) {
}
