package com.payments.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FraudScoreTest {

    @Test
    void ruleBasedBlock_alwaysReturnsBlock_regardlessOfMlScore() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.BLOCK, List.of("PAN_VELOCITY"));
        var withMl = score.withMlScore(new BigDecimal("0.01"));
        assertThat(withMl.effectiveLevel()).isEqualTo(RiskLevel.BLOCK);
    }

    @Test
    void ruleBasedBlock_alwaysReturnsBlock_evenWithZeroMlScore() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.BLOCK, List.of("IMPOSSIBLE_TRAVEL"))
                .withMlScore(BigDecimal.ZERO);
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.BLOCK);
    }

    @Test
    void noMlScore_returnsRuleBasedLevel() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.MEDIUM, List.of("ROUND_AMOUNT"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void mlScoreAbove85_escalatesToBlock() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.LOW, List.of())
                .withMlScore(new BigDecimal("0.86"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.BLOCK);
    }

    @Test
    void mlScoreExactly85_doesNotEscalateToBlock() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.HIGH, List.of())
                .withMlScore(new BigDecimal("0.85"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void mlScoreAbove60_withMediumRuleBase_escalatesToHigh() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.MEDIUM, List.of("FIRST_TXN_HIGH_AMOUNT"))
                .withMlScore(new BigDecimal("0.61"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void mlScoreAbove60_withHighRuleBase_remainsHigh() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.HIGH, List.of("TERMINAL_VELOCITY"))
                .withMlScore(new BigDecimal("0.70"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void mlScoreAbove60_withLowRuleBase_remainsLow() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.LOW, List.of())
                .withMlScore(new BigDecimal("0.65"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void mlScoreBelow60_doesNotElevateLevel() {
        var score = FraudScore.ruleBasedOnly(RiskLevel.MEDIUM, List.of("ROUND_AMOUNT"))
                .withMlScore(new BigDecimal("0.59"));
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void triggeredRules_areImmutable() {
        var rules = new java.util.ArrayList<>(List.of("RULE_A"));
        var score = FraudScore.ruleBasedOnly(RiskLevel.LOW, rules);
        rules.add("RULE_B");
        assertThat(score.triggeredRules()).containsExactly("RULE_A");
    }

    @Test
    void nullRuleBasedLevel_throws() {
        assertThatThrownBy(() -> FraudScore.ruleBasedOnly(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
