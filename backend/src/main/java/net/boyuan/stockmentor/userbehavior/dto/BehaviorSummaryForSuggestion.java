package net.boyuan.stockmentor.userbehavior.dto;

import net.boyuan.stockmentor.userbehavior.model.ConcentrationLevel;
import net.boyuan.stockmentor.userbehavior.model.HighVolatilityExposure;
import net.boyuan.stockmentor.userbehavior.model.TurnoverLevel;
import net.boyuan.stockmentor.userbehavior.model.UserBehaviorStyle;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BehaviorSummaryForSuggestion(
        Long behaviorProfileId,
        LocalDate analysisStartDate,
        LocalDate analysisEndDate,
        Integer behaviorRiskScore,
        UserBehaviorStyle behaviorStyle,
        BehaviorConfidence behaviorConfidence,
        BigDecimal averagePositionSizePercent,
        TurnoverLevel turnoverLevel,
        ConcentrationLevel concentrationLevel,
        HighVolatilityExposure highVolatilityExposure,
        Integer stockRiskExposureScore,
        Integer concentrationScore,
        Integer turnoverScore,
        Integer holdingPeriodScore,
        Integer volatilityExposureScore,
        LocalDateTime updatedAt,
        String sourceNote
) {
    public boolean hasProfile() {
        return behaviorProfileId != null;
    }
}
