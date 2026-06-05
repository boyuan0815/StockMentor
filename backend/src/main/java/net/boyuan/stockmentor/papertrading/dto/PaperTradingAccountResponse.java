package net.boyuan.stockmentor.papertrading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperTradingAccountResponse(
        Long accountId,
        BigDecimal cashBalance,
        BigDecimal startingCash,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
