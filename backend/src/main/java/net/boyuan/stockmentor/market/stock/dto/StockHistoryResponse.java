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
        BigDecimal previousClose,
        BigDecimal displayedAbsoluteChange,
        LocalDateTime displayedMarketTime,
        LocalDateTime targetDisplayMarketTime,
        Integer dataDelayMinutes,
        String priceFreshnessStatus,
        String priceFreshnessLabel,
        Boolean isPriceAvailable,
        Boolean isTradeExecutable,
        String dataNote,
        String priceSource,
        String marketTimeZone,
        String granularity,
        Boolean lineChartSupported,
        Boolean candlestickSupported,
        Integer expectedPointCount,
        Integer actualPointCount,
        Integer missingDataCount,
        Integer includedTradingDays,
        Integer requestedTradingDays,
        String timezone,
        String dataSource,
        Boolean isFallback,
        String completenessNote
) {
}
