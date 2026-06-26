package net.boyuan.stockmentor.market.stock.model;

public enum DelayedPriceFreshnessStatus {
    DELAYED_15_MINUTES("Delayed 15 min"),
    MARKET_CLOSED_LAST_CLOSE("Market Closed · Last Close"),
    LATEST_STORED_PRICE("Latest Stored Price"),
    UNAVAILABLE("Unavailable"),

    // Legacy names kept so older tests/fixtures compile; new responses should use the canonical names above.
    AVAILABLE,
    STALE,
    NOT_READY,
    NOT_READY_WITH_DAILY_FALLBACK,
    FALLBACK_DAILY,
    MARKET_CLOSED,
    MARKET_CLOSED_PENDING_DAILY_CLOSE;

    private final String label;

    DelayedPriceFreshnessStatus() {
        this.label = switch (name()) {
            case "AVAILABLE", "STALE" -> "Delayed 15 min";
            case "MARKET_CLOSED" -> "Market Closed · Last Close";
            case "MARKET_CLOSED_PENDING_DAILY_CLOSE", "FALLBACK_DAILY",
                    "NOT_READY", "NOT_READY_WITH_DAILY_FALLBACK" -> "Latest Stored Price";
            default -> "Unavailable";
        };
    }

    DelayedPriceFreshnessStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
