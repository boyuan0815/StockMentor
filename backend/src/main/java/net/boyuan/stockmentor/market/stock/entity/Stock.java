package net.boyuan.stockmentor.market.stock.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock")
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    @Column(name = "symbol", length = 10)
    private String symbol;

    @Column(name = "company_name")
    private String companyName;

    //    latest close price
    //     Double : 0.1 + 0.2 = 0.30000000000004
    @Column(name = "current_price", precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "day_open", precision = 19, scale = 6)
    private BigDecimal dayOpen;

    @Column(name = "day_high", precision = 19, scale = 6)
    private BigDecimal dayHigh;

    @Column(name = "day_low", precision = 19, scale = 6)
    private BigDecimal dayLow;

    @Column(name = "percent_change", precision = 5, scale = 2)
    private BigDecimal percentChange;

//    determine whether data is new or not (now - lastUpdated > x min)
//    ui : last updated: 15:59
//    avoid duplicate fetch
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "is_market_open")
    private Boolean isMarketOpen;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "source")
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // for debugging & audittrail purpose
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


}
