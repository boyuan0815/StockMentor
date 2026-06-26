package net.boyuan.stockmentor.papertrading.dto;

import java.util.List;

public record PaperTradeTransactionPageResponse(
        List<PaperTradeTransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
