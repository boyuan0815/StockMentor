package net.boyuan.stockmentor.market.stockdaily.entity;

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
        name = "stock_price_daily",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_symbol_trading_date",
                        columnNames = {"symbol", "trading_date"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_daily_symbol_trading_date",
                        columnList = "symbol, trading_date"
                )
        }
)
public class StockPriceDaily {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dailyId;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "trading_date", nullable = false)
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

    @Column(name = "source")
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
