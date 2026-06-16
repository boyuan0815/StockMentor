package net.boyuan.stockmentor.papertrading.dto;

import net.boyuan.stockmentor.market.stock.dto.DelayedPriceMetadataResponse;

public record PaperTradeExecutionResponse(
        PaperTradingAccountResponse account,
        PaperPositionResponse position,
        PaperTradeTransactionResponse transaction,
        DelayedPriceMetadataResponse delayedPriceMetadata
) {
}
