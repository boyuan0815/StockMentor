package net.boyuan.stockmentor.analysis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "stock_analysis_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_analysis_symbol_timeframe_hash",
                        columnNames = {"symbol", "timeframe", "snapshot_hash"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_analysis_symbol_timeframe_created",
                        columnList = "symbol, timeframe, created_at"
                )
        }
)
public class StockAnalysisSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long analysisSnapshotId;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "timeframe", length = 10, nullable = false)
    private String timeframe;

    @Column(name = "data_start_date")
    private LocalDate dataStartDate;

    @Column(name = "data_end_date")
    private LocalDate dataEndDate;

    @Column(name = "current_price", precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(name = "avg_volume", precision = 19, scale = 2)
    private BigDecimal avgVolume;

    @Column(name = "avg_range", precision = 19, scale = 6)
    private BigDecimal avgRange;

    @Column(name = "volatility_score", precision = 10, scale = 4)
    private BigDecimal volatilityScore;

    @Column(name = "trend_strength", precision = 10, scale = 4)
    private BigDecimal trendStrength;

    @Column(name = "percent_change", precision = 10, scale = 4)
    private BigDecimal percentChange;

    @Column(name = "high_price", precision = 19, scale = 6)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 19, scale = 6)
    private BigDecimal lowPrice;

    @Column(name = "trend")
    private String trend;

    @Column(name = "volatility_label")
    private String volatilityLabel;

    @Column(name = "volume_trend")
    private String volumeTrend;

    @Column(name = "price_consistency")
    private String priceConsistency;

    @Column(name = "risk_category")
    private String riskCategory;

    @Column(name = "baseline_risk_category")
    private String baselineRiskCategory;

    @Column(name = "data_source")
    private String dataSource;

    @Column(name = "is_fallback")
    private Boolean isFallback;

    @Column(name = "missing_data_count")
    private Integer missingDataCount;

    @Column(name = "snapshot_hash", length = 64, nullable = false)
    private String snapshotHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
