package com.payments.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// LEARN: OptionalField — mlRiskScore is Optional<BigDecimal> because ML port is not wired pre-June
public record FraudScore(
        RiskLevel ruleBasedLevel,
        Optional<BigDecimal> mlRiskScore,
        List<String> triggeredRules
) {
    public FraudScore {
        Objects.requireNonNull(ruleBasedLevel, "ruleBasedLevel must not be null");
        Objects.requireNonNull(mlRiskScore, "mlRiskScore must not be null");
        Objects.requireNonNull(triggeredRules, "triggeredRules must not be null");
        triggeredRules = List.copyOf(triggeredRules);
    }

    public RiskLevel effectiveLevel() {
        if (ruleBasedLevel == RiskLevel.BLOCK) return RiskLevel.BLOCK;
        return mlRiskScore
                .map(score -> elevate(ruleBasedLevel, score))
                .orElse(ruleBasedLevel);
    }

    public static FraudScore ruleBasedOnly(RiskLevel level, List<String> triggeredRules) {
        return new FraudScore(level, Optional.empty(), triggeredRules);
    }

    public FraudScore withMlScore(BigDecimal mlScore) {
        Objects.requireNonNull(mlScore, "mlScore must not be null");
        return new FraudScore(ruleBasedLevel, Optional.of(mlScore), triggeredRules);
    }

    private RiskLevel elevate(RiskLevel base, BigDecimal mlScore) {
        if (mlScore.compareTo(new BigDecimal("0.85")) > 0) return RiskLevel.BLOCK;
        if (mlScore.compareTo(new BigDecimal("0.60")) > 0 && base == RiskLevel.MEDIUM) return RiskLevel.HIGH;
        return base;
    }
}
