package net.boyuan.stockmentor.papertrading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaperTradeTransactionResponse(
        Long transactionId,
        String symbol,
        String side,
        Integer quantity,
        BigDecimal executionPrice,
        BigDecimal grossAmount,
        BigDecimal cashBalanceAfter,
        LocalDateTime executedAt
) {
}
