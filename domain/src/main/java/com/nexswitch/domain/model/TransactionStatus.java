package com.nexswitch.domain.model;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

// LEARN: StateMachine — VALID_TRANSITIONS Map gives O(1) canTransitionTo; enum keys prevent typos
public enum TransactionStatus {
    INITIATED,
    AUTHORIZATION_PENDING,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    REVERSAL_PENDING,
    REVERSED,
    UNKNOWN,
    REFUND_INITIATED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED,
    SETTLEMENT_PENDING,
    RECONCILED,
    PAID_OUT,
    CHARGEBACK_RECEIVED,
    CHARGEBACK_CONTESTED,
    CHARGEBACK_EVIDENCE_SUBMITTED,
    CHARGEBACK_WON,
    CHARGEBACK_LOST;

    private static final Map<TransactionStatus, Set<TransactionStatus>> VALID_TRANSITIONS =
        Map.ofEntries(
            entry(INITIATED,             Set.of(AUTHORIZATION_PENDING, DECLINED)),
            entry(AUTHORIZATION_PENDING, Set.of(AUTHORIZED, DECLINED, REVERSAL_PENDING, UNKNOWN)),
            entry(AUTHORIZED,            Set.of(CAPTURED, REVERSAL_PENDING, REFUND_INITIATED)),
            entry(CAPTURED,              Set.of(SETTLEMENT_PENDING, REFUND_INITIATED)),
            entry(REVERSAL_PENDING,      Set.of(REVERSED, UNKNOWN)),
            entry(UNKNOWN,               Set.of(AUTHORIZED, REVERSED, DECLINED)),
            entry(REFUND_INITIATED,      Set.of(REFUND_PENDING, REFUND_FAILED)),
            entry(REFUND_PENDING,        Set.of(REFUNDED, REFUND_FAILED)),
            entry(SETTLEMENT_PENDING,    Set.of(RECONCILED)),
            entry(RECONCILED,            Set.of(PAID_OUT, CHARGEBACK_RECEIVED)),
            entry(PAID_OUT,              Set.of(CHARGEBACK_RECEIVED, REFUND_INITIATED)),
            entry(CHARGEBACK_RECEIVED,   Set.of(CHARGEBACK_CONTESTED, CHARGEBACK_LOST)),
            entry(CHARGEBACK_CONTESTED,  Set.of(CHARGEBACK_EVIDENCE_SUBMITTED)),
            entry(CHARGEBACK_EVIDENCE_SUBMITTED, Set.of(CHARGEBACK_WON, CHARGEBACK_LOST))
        );

    public boolean canTransitionTo(TransactionStatus target) {
        Set<TransactionStatus> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
