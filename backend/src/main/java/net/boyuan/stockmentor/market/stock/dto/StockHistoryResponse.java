package net.boyuan.stockmentor.market.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockHistoryResponse(
        String symbol,
        String timeframe,
        String source,
        List<StockHistoryPointResponse> points,
        String message,
        BigDecimal displayedPrice,
        BigDecimal displayedPercentChange,
        LocalDateTime displayedMarketTime,
        LocalDateTime targetDisplayMarketTime,
        Integer dataDelayMinutes,
        String priceFreshnessStatus,
        Boolean isPriceAvailable,
        Boolean isTradeExecutable,
        String dataNote,
        String priceSource,
        String marketTimeZone
) {
}
