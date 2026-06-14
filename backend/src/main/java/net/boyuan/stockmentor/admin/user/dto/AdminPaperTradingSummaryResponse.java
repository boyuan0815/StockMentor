package net.boyuan.stockmentor.admin.user.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaperTradingSummaryResponse(
        String accountStatus,
        BigDecimal cashBalance,
        BigDecimal realizedProfitLoss,
        Integer currentSessionNumber,
        long positionCount,
        long transactionCount,
        LocalDateTime lastResetAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
