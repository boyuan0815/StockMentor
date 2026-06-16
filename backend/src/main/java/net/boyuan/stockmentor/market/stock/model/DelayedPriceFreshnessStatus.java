package net.boyuan.stockmentor.market.stock.model;

public enum DelayedPriceFreshnessStatus {
    AVAILABLE,
    STALE,
    NOT_READY,
    NOT_READY_WITH_DAILY_FALLBACK,
    FALLBACK_DAILY,
    MARKET_CLOSED,
    MARKET_CLOSED_PENDING_DAILY_CLOSE,
    UNAVAILABLE
}
