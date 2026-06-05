package net.boyuan.stockmentor.userbehavior.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.userbehavior.model.ConcentrationLevel;
import net.boyuan.stockmentor.userbehavior.model.HighVolatilityExposure;
import net.boyuan.stockmentor.userbehavior.model.TurnoverLevel;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "user_behavior_profile",
        indexes = {
                @Index(name = "idx_behavior_profile_user_updated", columnList = "user_id, updated_at"),
                @Index(name = "idx_behavior_profile_user_confidence", columnList = "user_id, behavior_confidence")
        }
)
public class UserBehaviorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long behaviorProfileId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "analysis_start_date")
    private LocalDate analysisStartDate;

    @Column(name = "analysis_end_date")
    private LocalDate analysisEndDate;

    @Column(name = "behavior_risk_score")
    private Integer behaviorRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior_style", length = 40, nullable = false)
    private UserBehaviorStyle behaviorStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior_confidence", length = 20, nullable = false)
    private BehaviorConfidence behaviorConfidence;

    @Column(name = "average_position_size_percent", precision = 8, scale = 2)
    private BigDecimal averagePositionSizePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "turnover_level", length = 20)
    private TurnoverLevel turnoverLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "concentration_level", length = 30)
    private ConcentrationLevel concentrationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "high_volatility_exposure", length = 20)
    private HighVolatilityExposure highVolatilityExposure;

    @Column(name = "stock_risk_exposure_score")
    private Integer stockRiskExposureScore;

    @Column(name = "concentration_score")
    private Integer concentrationScore;

    @Column(name = "turnover_score")
    private Integer turnoverScore;

    @Column(name = "holding_period_score")
    private Integer holdingPeriodScore;

    @Column(name = "volatility_exposure_score")
    private Integer volatilityExposureScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
