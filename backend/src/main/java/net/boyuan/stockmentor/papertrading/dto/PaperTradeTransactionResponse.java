package net.boyuan.stockmentor.papertrading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperTradeTransactionResponse(
        Long transactionId,
        String symbol,
        String side,
        Integer quantity,
        BigDecimal executionPrice,
        BigDecimal price,
        BigDecimal grossAmount,
        BigDecimal fee,
        BigDecimal netAmount,
        BigDecimal totalAmount,
        BigDecimal realizedProfitLoss,
        BigDecimal cashBalanceAfter,
        Boolean isCurrentSession,
        Integer sessionNumber,
        LocalDateTime executedAt,
        LocalDateTime transactionTime
) {
}
