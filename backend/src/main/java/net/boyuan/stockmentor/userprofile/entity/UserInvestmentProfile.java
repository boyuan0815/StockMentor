package net.boyuan.stockmentor.userprofile.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.userprofile.model.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "user_investment_profile",
        indexes = {
                @Index(name = "idx_profile_user_updated", columnList = "user_id, updated_at"),
                @Index(name = "idx_profile_user_version", columnList = "user_id, profile_version")
        }
)
public class UserInvestmentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long profileId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tolerance", nullable = false, length = 30)
    private RiskTolerance riskTolerance;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_goal", nullable = false, length = 50)
    private InvestmentGoal investmentGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 30)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_volatility", nullable = false, length = 30)
    private PreferredVolatility preferredVolatility;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_horizon", nullable = false, length = 30)
    private PreferredHorizon preferredHorizon;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "goal_score")
    private Integer goalScore;

    @Column(name = "experience_score")
    private Integer experienceScore;

    @Column(name = "behavior_risk_score")
    private Integer behaviorRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior_style", length = 50)
    private BehaviorStyle behaviorStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior_confidence", length = 20)
    private BehaviorConfidence behaviorConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_source", nullable = false, length = 30)
    private ProfileSource profileSource;

    @Column(name = "profile_version", nullable = false)
    private Integer profileVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
