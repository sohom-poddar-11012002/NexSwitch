package com.nexswitch.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

// LEARN: EnumMap — array-indexed by enum ordinal; O(1) lookup with no hashing overhead.
//        StateMachine — VALID_TRANSITIONS gives O(1) canTransitionTo; enum keys prevent typos.
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
    CHARGEBACK_LOST,
    SETTLED,
    PAYOUT_INITIATED,
    RESERVE_RELEASED,
    EXPIRED;

    private static final Map<TransactionStatus, Set<TransactionStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(TransactionStatus.class);
        VALID_TRANSITIONS.put(INITIATED,             Set.of(AUTHORIZATION_PENDING, DECLINED));
        VALID_TRANSITIONS.put(AUTHORIZATION_PENDING, Set.of(AUTHORIZED, DECLINED, REVERSAL_PENDING, UNKNOWN));
        VALID_TRANSITIONS.put(AUTHORIZED,            Set.of(CAPTURED, REVERSAL_PENDING, REFUND_INITIATED, EXPIRED));
        VALID_TRANSITIONS.put(CAPTURED,              Set.of(SETTLEMENT_PENDING, REFUND_INITIATED));
        VALID_TRANSITIONS.put(REVERSAL_PENDING,      Set.of(REVERSED, UNKNOWN));
        VALID_TRANSITIONS.put(UNKNOWN,               Set.of(AUTHORIZED, REVERSED, DECLINED));
        VALID_TRANSITIONS.put(REFUND_INITIATED,      Set.of(REFUND_PENDING, REFUND_FAILED));
        VALID_TRANSITIONS.put(REFUND_PENDING,        Set.of(REFUNDED, REFUND_FAILED));
        VALID_TRANSITIONS.put(SETTLEMENT_PENDING,    Set.of(RECONCILED));
        VALID_TRANSITIONS.put(RECONCILED,            Set.of(PAID_OUT, CHARGEBACK_RECEIVED, SETTLED));
        VALID_TRANSITIONS.put(PAID_OUT,              Set.of(CHARGEBACK_RECEIVED, REFUND_INITIATED, PAYOUT_INITIATED));
        VALID_TRANSITIONS.put(SETTLED,               Set.of(PAYOUT_INITIATED));
        VALID_TRANSITIONS.put(PAYOUT_INITIATED,      Set.of(RESERVE_RELEASED));
        VALID_TRANSITIONS.put(CHARGEBACK_RECEIVED,   Set.of(CHARGEBACK_CONTESTED, CHARGEBACK_LOST));
        VALID_TRANSITIONS.put(CHARGEBACK_CONTESTED,  Set.of(CHARGEBACK_EVIDENCE_SUBMITTED));
        VALID_TRANSITIONS.put(CHARGEBACK_EVIDENCE_SUBMITTED, Set.of(CHARGEBACK_WON, CHARGEBACK_LOST));
    }

    public boolean canTransitionTo(TransactionStatus target) {
        Set<TransactionStatus> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
