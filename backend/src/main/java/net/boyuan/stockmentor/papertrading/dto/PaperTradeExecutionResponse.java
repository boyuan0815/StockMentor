package net.boyuan.stockmentor.papertrading.dto;

public record PaperTradeExecutionResponse(
        PaperTradingAccountResponse account,
        PaperPositionResponse position,
        PaperTradeTransactionResponse transaction
) {
}
