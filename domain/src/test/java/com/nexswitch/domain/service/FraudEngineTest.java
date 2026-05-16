package com.nexswitch.domain.service;

import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.PanHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Currency;

import static com.nexswitch.domain.model.RiskLevel.*;
import static com.nexswitch.domain.service.FraudEngine.*;
import static org.assertj.core.api.Assertions.*;

class FraudEngineTest {

    private static final Currency INR = Currency.getInstance("INR");

    private FraudEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FraudEngine();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FraudScoringContext context(String mcc, String amount) {
        return new FraudScoringContext(
            PanHash.of("a".repeat(64)),
            Money.of(amount, INR),
            mcc,
            PaymentNetwork.VISA,
            PaymentMethod.CARD_CHIP,
            Instant.now()
        );
    }

    private FraudVelocityData clean() {
        return new FraudVelocityData(0, 0, 0, false, false);
    }

    // ── Clean transaction — baseline LOW ─────────────────────────────────────

    @Test
    void cleanTransaction_returnsLow_withNoTriggeredRules() {
        FraudScore score = engine.score(context("5411", "6000.00"), clean());

        assertThat(score.ruleBasedLevel()).isEqualTo(LOW);
        assertThat(score.triggeredRules()).isEmpty();
        assertThat(score.mlRiskScore()).isEmpty();
    }

    // ── PAN velocity (5 min) — BLOCK ─────────────────────────────────────────

    @Test
    void panVelocity_moreThan3In5Min_blocks() {
        var velocity = new FraudVelocityData(4, 0, 0, false, false);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(BLOCK);
        assertThat(score.triggeredRules()).contains(RULE_PAN_VELOCITY_5MIN);
    }

    @Test
    void panVelocity_exactly3In5Min_doesNotBlock() {
        var velocity = new FraudVelocityData(3, 0, 0, false, false);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isNotEqualTo(BLOCK);
        assertThat(score.triggeredRules()).doesNotContain(RULE_PAN_VELOCITY_5MIN);
    }

    // ── PAN velocity (1 hour) — BLOCK ────────────────────────────────────────

    @Test
    void panVelocity_moreThan10InHour_blocks() {
        var velocity = new FraudVelocityData(0, 11, 0, false, false);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(BLOCK);
        assertThat(score.triggeredRules()).contains(RULE_PAN_VELOCITY_1HOUR);
    }

    @Test
    void panVelocity_exactly10InHour_doesNotBlock() {
        var velocity = new FraudVelocityData(0, 10, 0, false, false);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isNotEqualTo(BLOCK);
        assertThat(score.triggeredRules()).doesNotContain(RULE_PAN_VELOCITY_1HOUR);
    }

    // ── Impossible travel — BLOCK ─────────────────────────────────────────────

    @Test
    void impossibleTravel_blocks() {
        var velocity = new FraudVelocityData(0, 0, 0, false, true);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(BLOCK);
        assertThat(score.triggeredRules()).contains(RULE_IMPOSSIBLE_TRAVEL);
    }

    // ── Terminal velocity — HIGH ──────────────────────────────────────────────

    @Test
    void terminalVelocity_moreThan200InHour_flagsHigh() {
        var velocity = new FraudVelocityData(0, 0, 201, false, false);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(HIGH);
        assertThat(score.triggeredRules()).contains(RULE_TERMINAL_VELOCITY);
    }

    @Test
    void terminalVelocity_exactly200InHour_doesNotFlag() {
        var velocity = new FraudVelocityData(0, 0, 200, false, false);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isNotEqualTo(HIGH);
        assertThat(score.triggeredRules()).doesNotContain(RULE_TERMINAL_VELOCITY);
    }

    // ── First-transaction high-amount — HIGH ──────────────────────────────────

    @Test
    void firstTransaction_amountOver50k_flagsHigh() {
        var velocity = new FraudVelocityData(0, 0, 0, true, false);
        FraudScore score = engine.score(context("5411", "50000.01"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(HIGH);
        assertThat(score.triggeredRules()).contains(RULE_FIRST_TXN_HIGH_AMOUNT);
    }

    @Test
    void firstTransaction_amountExactly50k_doesNotFlag() {
        var velocity = new FraudVelocityData(0, 0, 0, true, false);
        FraudScore score = engine.score(context("5411", "50000.00"), velocity);

        assertThat(score.triggeredRules()).doesNotContain(RULE_FIRST_TXN_HIGH_AMOUNT);
    }

    @Test
    void firstTransaction_amountBelow50k_doesNotFlag() {
        var velocity = new FraudVelocityData(0, 0, 0, true, false);
        FraudScore score = engine.score(context("5411", "49999.99"), velocity);

        assertThat(score.triggeredRules()).doesNotContain(RULE_FIRST_TXN_HIGH_AMOUNT);
    }

    @Test
    void notFirstTransaction_amountOver50k_doesNotFlag() {
        var velocity = new FraudVelocityData(0, 0, 0, false, false);
        FraudScore score = engine.score(context("5411", "100000.00"), velocity);

        assertThat(score.triggeredRules()).doesNotContain(RULE_FIRST_TXN_HIGH_AMOUNT);
    }

    // ── High-risk MCC — HIGH ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"5094", "5944", "7995"})
    void highRiskMcc_flagsHigh(String mcc) {
        FraudScore score = engine.score(context(mcc, "1000.00"), clean());

        assertThat(score.ruleBasedLevel()).isEqualTo(HIGH);
        assertThat(score.triggeredRules()).contains(RULE_HIGH_RISK_MCC);
    }

    @Test
    void normalMcc_doesNotFlag() {
        FraudScore score = engine.score(context("5411", "1000.00"), clean());

        assertThat(score.triggeredRules()).doesNotContain(RULE_HIGH_RISK_MCC);
    }

    // ── Round amount — MEDIUM ─────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "10000.00",
        "20000.00",
        "50000.00",
        "100000.00"
    })
    void roundAmountAtOrAbove10k_flagsMedium(String amount) {
        FraudScore score = engine.score(context("5411", amount), clean());

        assertThat(score.ruleBasedLevel()).isEqualTo(MEDIUM);
        assertThat(score.triggeredRules()).contains(RULE_ROUND_AMOUNT);
    }

    @ParameterizedTest
    @CsvSource({
        "6000.00",
        "9999.99",
        "15000.50"
    })
    void nonRoundAmount_doesNotFlag(String amount) {
        FraudScore score = engine.score(context("5411", amount), clean());

        assertThat(score.triggeredRules()).doesNotContain(RULE_ROUND_AMOUNT);
    }

    @Test
    void roundAmountBelow10k_doesNotFlag() {
        FraudScore score = engine.score(context("5411", "5000.00"), clean());

        assertThat(score.triggeredRules()).doesNotContain(RULE_ROUND_AMOUNT);
    }

    // ── Multiple rules — highest severity wins ────────────────────────────────

    @Test
    void blockPlusMedium_returnsBlock() {
        var velocity = new FraudVelocityData(4, 0, 0, false, false);
        FraudScore score = engine.score(context("5411", "10000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(BLOCK);
        assertThat(score.triggeredRules()).containsExactlyInAnyOrder(
            RULE_PAN_VELOCITY_5MIN, RULE_ROUND_AMOUNT
        );
    }

    @Test
    void allBlockRulesFire_allCollected() {
        var velocity = new FraudVelocityData(4, 11, 0, false, true);
        FraudScore score = engine.score(context("5411", "1000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(BLOCK);
        assertThat(score.triggeredRules()).containsExactlyInAnyOrder(
            RULE_PAN_VELOCITY_5MIN, RULE_PAN_VELOCITY_1HOUR, RULE_IMPOSSIBLE_TRAVEL
        );
    }

    @Test
    void highAndMediumRulesBothFire_returnsHigh() {
        var velocity = new FraudVelocityData(0, 0, 201, false, false);
        FraudScore score = engine.score(context("5411", "10000.00"), velocity);

        assertThat(score.ruleBasedLevel()).isEqualTo(HIGH);
        assertThat(score.triggeredRules()).containsExactlyInAnyOrder(
            RULE_TERMINAL_VELOCITY, RULE_ROUND_AMOUNT
        );
    }

    @Test
    void highRiskMccAndRoundAmount_allTriggeredRulesCollected() {
        FraudScore score = engine.score(context("7995", "20000.00"), clean());

        assertThat(score.ruleBasedLevel()).isEqualTo(HIGH);
        assertThat(score.triggeredRules()).containsExactlyInAnyOrder(
            RULE_HIGH_RISK_MCC, RULE_ROUND_AMOUNT
        );
    }

    // ── FraudVelocityData validation ──────────────────────────────────────────

    @Test
    void negativeVelocityCounts_throw() {
        assertThatThrownBy(() -> new FraudVelocityData(-1, 0, 0, false, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FraudVelocityData(0, -1, 0, false, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FraudVelocityData(0, 0, -1, false, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── ML port — score merging ───────────────────────────────────────────────

    @Test
    void mlPort_notPresent_scoreHasEmptyMlRiskScore() {
        FraudScore score = engine.score(context("5411", "6000.00"), clean());
        assertThat(score.mlRiskScore()).isEmpty();
    }

    @Test
    void mlPort_returnsScore_mergedIntoFraudScore() {
        var ml = (com.nexswitch.domain.port.outbound.FraudScoringPort)
            (ctx, budget) -> java.util.Optional.of(new java.math.BigDecimal("0.30"));
        var engineWithMl = new FraudEngine(ml);

        FraudScore score = engineWithMl.score(context("5411", "6000.00"), clean());

        assertThat(score.mlRiskScore()).hasValue(new java.math.BigDecimal("0.30"));
        assertThat(score.effectiveLevel()).isEqualTo(LOW); // 0.30 doesn't elevate LOW
    }

    @Test
    void mlPort_highScore_elevatesMediumToBlock() {
        var ml = (com.nexswitch.domain.port.outbound.FraudScoringPort)
            (ctx, budget) -> java.util.Optional.of(new java.math.BigDecimal("0.90"));
        var engineWithMl = new FraudEngine(ml);

        // Round amount triggers MEDIUM rule-based; ML 0.90 elevates to BLOCK
        FraudScore score = engineWithMl.score(context("5411", "10000.00"), clean());

        assertThat(score.ruleBasedLevel()).isEqualTo(MEDIUM);
        assertThat(score.effectiveLevel()).isEqualTo(RiskLevel.BLOCK);
    }

    @Test
    void mlPort_notCalledWhenRuleEngineAlreadyBlocks() {
        boolean[] called = {false};
        var ml = (com.nexswitch.domain.port.outbound.FraudScoringPort) (ctx, budget) -> {
            called[0] = true;
            return java.util.Optional.of(new java.math.BigDecimal("0.10"));
        };
        var engineWithMl = new FraudEngine(ml);

        // PAN velocity > 3 → rule engine BLOCK → ML should be skipped
        var velocity = new FraudVelocityData(4, 0, 0, false, false);
        engineWithMl.score(context("5411", "1000.00"), velocity);

        assertThat(called[0]).isFalse();
    }

    @Test
    void mlPort_returnsEmpty_scoreHasEmptyMlRiskScore() {
        var ml = (com.nexswitch.domain.port.outbound.FraudScoringPort)
            (ctx, budget) -> java.util.Optional.empty();
        var engineWithMl = new FraudEngine(ml);

        FraudScore score = engineWithMl.score(context("5411", "6000.00"), clean());

        assertThat(score.mlRiskScore()).isEmpty();
        assertThat(score.effectiveLevel()).isEqualTo(LOW);
    }

    @Test
    void nullMlPort_throwsOnConstruction() {
        assertThatThrownBy(() -> new FraudEngine(null))
            .isInstanceOf(NullPointerException.class);
    }

    // ── Null guards ──────────────────────────────────────────────────────────

    @Test
    void nullContext_throws() {
        assertThatThrownBy(() -> engine.score(null, clean()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullVelocity_throws() {
        assertThatThrownBy(() -> engine.score(context("5411", "1000.00"), null))
            .isInstanceOf(NullPointerException.class);
    }
}
