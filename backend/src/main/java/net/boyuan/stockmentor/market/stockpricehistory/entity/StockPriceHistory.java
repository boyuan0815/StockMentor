    package net.boyuan.stockmentor.market.stockpricehistory.entity;

    import jakarta.persistence.*;
    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;
    import net.boyuan.stockmentor.market.stock.entity.Stock;

    import java.math.BigDecimal;
    import java.time.LocalDate;
    import java.time.LocalDateTime;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Entity
    @Table(
            name = "stock_price_history_1min",
            uniqueConstraints = {
                    @UniqueConstraint(
                            name = "uk_1min_symbol_timestamp",
                            columnNames = {"symbol", "timestamp"}
                    )
            },
            indexes = {
                    @Index(
                            name = "idx_1min_symbol_timestamp",
                            columnList = "symbol, timestamp"
                    ),
                    @Index(
                            name = "idx_1min_symbol_trading_date",
                            columnList = "symbol, trading_date"
                    )
            }
    )
    public class StockPriceHistory {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long historyId;

        // each stock has MANY stock price history
        // each stock price history owned by one stock
        @ManyToOne
        @JoinColumn(name = "stock_id")
        private Stock stock;

        @Column(name = "symbol")
        private String symbol;

        // stock market time
        @Column(name = "timestamp")
        private LocalDateTime timestamp;

        @Column(name = "trading_date")
        private LocalDate tradingDate;

        @Column(name = "open_price", precision = 19, scale = 6)
        private BigDecimal openPrice;

        @Column(name = "high_price", precision = 19, scale = 6)
        private BigDecimal highPrice;

        @Column(name = "low_price", precision = 19, scale = 6)
        private BigDecimal lowPrice;

        @Column(name = "close_price", precision = 19, scale = 6)
        private BigDecimal closePrice;

        @Column(name = "volume")
        private Long volume;

        // currently will only put 1min, for other timeIntervals will be calculated and returned to the frontend directly to keep database simple
        @Column(name = "time_interval")
        private String timeInterval;

        @Column(name = "source")
        private String source;

        // db insert time - for debugging & audit_trail purpose
        @Column(name = "created_at")
        private LocalDateTime createdAt;
    }
