package net.boyuan.stockmentor.common.util;

import java.util.Map;

//@NoArgsConstructor
public class StockMetadata {
    // since this is utility / constant class, use "private constructor" is better instead of @NoArgsConstructor
    private StockMetadata(){}

    public static final Map<String, String> COMPANY_MAP = Map.of(
            "NVDA", "NVIDIA",
            "TSLA", "Tesla",
            "AMD", "AMD",
            "AAPL", "Apple",
            "MSFT", "Microsoft",
            "GOOG", "Google",
            "KO", "Coca-Cola",
            "JNJ", "Johnson & Johnson"
    );

    public static final Map<String, String> RISK_CATEGORY_MAP = Map.of(
            "NVDA", "aggressive",
            "TSLA", "aggressive",
            "AMD", "aggressive",
            "AAPL", "moderate",
            "MSFT", "moderate",
            "GOOG", "moderate",
            "KO", "conservative",
            "JNJ", "conservative"
    );

    public static final String SYMBOLS = "NVDA,TSLA,AMD,AAPL,MSFT,GOOG,KO,JNJ";

}
