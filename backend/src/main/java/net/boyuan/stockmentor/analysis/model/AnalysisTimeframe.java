package net.boyuan.stockmentor.analysis.model;

public enum AnalysisTimeframe {
    ONE_DAY("1D", 1),
    FIVE_DAYS("5D", 5),
    SEVEN_DAYS("7D", 7),
    ONE_MONTH("1M", 21),
    THREE_MONTHS("3M", 63);

    private final String value;
    private final int tradingDays;

    AnalysisTimeframe(String value, int tradingDays) {
        this.value = value;
        this.tradingDays = tradingDays;
    }

    public String value() {
        return value;
    }

    public int tradingDays() {
        return tradingDays;
    }

    public static AnalysisTimeframe from(String value) {
        if (value == null || value.isBlank()) {
            return SEVEN_DAYS;
        }

        String normalized = value.trim().toUpperCase();
        for (AnalysisTimeframe timeframe : values()) {
            if (timeframe.value.equals(normalized)) {
                return timeframe;
            }
        }

        throw new IllegalArgumentException("Unsupported timeframe: " + value);
    }
}
