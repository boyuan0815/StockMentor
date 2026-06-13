package net.boyuan.stockmentor.market.stock.dto;

import java.util.List;

public record StockListResponse(
        Long userId,
        List<StockListItemResponse> stocks,
        String message
) {
}
