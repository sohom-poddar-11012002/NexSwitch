package com.payments.domain.service;

import com.payments.domain.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FraudEngine {

    public static final String RULE_PAN_VELOCITY_5MIN     = "PAN_VELOCITY_5MIN";
    public static final String RULE_PAN_VELOCITY_1HOUR    = "PAN_VELOCITY_1HOUR";
    public static final String RULE_IMPOSSIBLE_TRAVEL     = "IMPOSSIBLE_TRAVEL";
    public static final String RULE_TERMINAL_VELOCITY     = "TERMINAL_VELOCITY";
    public static final String RULE_FIRST_TXN_HIGH_AMOUNT = "FIRST_TXN_HIGH_AMOUNT";
    public static final String RULE_HIGH_RISK_MCC         = "HIGH_RISK_MCC";
    public static final String RULE_ROUND_AMOUNT          = "ROUND_AMOUNT";

    private static final Set<String> HIGH_RISK_MCCS = Set.of("5094", "5944", "7995");
    private static final BigDecimal FIFTY_THOUSAND  = new BigDecimal("50000.00");
    private static final BigDecimal TEN_THOUSAND    = new BigDecimal("10000.00");

    public FraudScore score(FraudScoringContext context, FraudVelocityData velocity) {
        Objects.requireNonNull(context,  "context must not be null");
        Objects.requireNonNull(velocity, "velocity must not be null");

        List<String> triggered = new ArrayList<>();
        RiskLevel maxLevel = RiskLevel.LOW;

        // BLOCK rules — all evaluated so every violated rule is captured
        if (velocity.panTransactionsLast5Min() > 3) {
            triggered.add(RULE_PAN_VELOCITY_5MIN);
            maxLevel = max(maxLevel, RiskLevel.BLOCK);
        }
        if (velocity.panTransactionsLastHour() > 10) {
            triggered.add(RULE_PAN_VELOCITY_1HOUR);
            maxLevel = max(maxLevel, RiskLevel.BLOCK);
        }
        if (velocity.isImpossibleTravel()) {
            triggered.add(RULE_IMPOSSIBLE_TRAVEL);
            maxLevel = max(maxLevel, RiskLevel.BLOCK);
        }

        // HIGH rules
        if (velocity.terminalTransactionsLastHour() > 200) {
            triggered.add(RULE_TERMINAL_VELOCITY);
            maxLevel = max(maxLevel, RiskLevel.HIGH);
        }
        if (velocity.isFirstTransactionOnPan()
                && context.amount().amount().compareTo(FIFTY_THOUSAND) > 0) {
            triggered.add(RULE_FIRST_TXN_HIGH_AMOUNT);
            maxLevel = max(maxLevel, RiskLevel.HIGH);
        }
        if (HIGH_RISK_MCCS.contains(context.merchantCategory())) {
            triggered.add(RULE_HIGH_RISK_MCC);
            maxLevel = max(maxLevel, RiskLevel.HIGH);
        }

        // MEDIUM rules
        if (isRoundAmount(context.amount().amount())) {
            triggered.add(RULE_ROUND_AMOUNT);
            maxLevel = max(maxLevel, RiskLevel.MEDIUM);
        }

        return FraudScore.ruleBasedOnly(maxLevel, triggered);
    }

    private static boolean isRoundAmount(BigDecimal amount) {
        return amount.compareTo(TEN_THOUSAND) >= 0
            && amount.remainder(TEN_THOUSAND).compareTo(BigDecimal.ZERO) == 0;
    }

    private static RiskLevel max(RiskLevel a, RiskLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
