package net.boyuan.stockmentor.admin.user.dto;

import net.boyuan.stockmentor.userprofile.dto.BehaviorProfileSummaryResponse;

public record AdminUserDetailResponse(
        AdminUserListItemResponse user,
        AdminInvestmentProfileSummaryResponse latestInvestmentProfile,
        BehaviorProfileSummaryResponse behaviorSummary,
        AdminPaperTradingSummaryResponse paperTradingSummary
) {
}
