package com.nexswitch.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.nexswitch.domain.model.TransactionStatus.*;
import static org.assertj.core.api.Assertions.*;

class TransactionStatusTest {

    @Test
    void enumHasTwentyStates() {
        assertThat(TransactionStatus.values()).hasSize(20);
    }

    @ParameterizedTest
    @CsvSource({
        "INITIATED,             AUTHORIZATION_PENDING",
        "INITIATED,             DECLINED",
        "AUTHORIZATION_PENDING, AUTHORIZED",
        "AUTHORIZATION_PENDING, DECLINED",
        "AUTHORIZATION_PENDING, REVERSAL_PENDING",
        "AUTHORIZATION_PENDING, UNKNOWN",
        "AUTHORIZED,            CAPTURED",
        "AUTHORIZED,            REVERSAL_PENDING",
        "AUTHORIZED,            REFUND_INITIATED",
        "CAPTURED,              SETTLEMENT_PENDING",
        "CAPTURED,              REFUND_INITIATED",
        "REVERSAL_PENDING,      REVERSED",
        "REVERSAL_PENDING,      UNKNOWN",
        "UNKNOWN,               AUTHORIZED",
        "UNKNOWN,               REVERSED",
        "UNKNOWN,               DECLINED",
        "REFUND_INITIATED,      REFUND_PENDING",
        "REFUND_INITIATED,      REFUND_FAILED",
        "REFUND_PENDING,        REFUNDED",
        "REFUND_PENDING,        REFUND_FAILED",
        "SETTLEMENT_PENDING,    RECONCILED",
        "RECONCILED,            PAID_OUT",
        "RECONCILED,            CHARGEBACK_RECEIVED",
        "PAID_OUT,              CHARGEBACK_RECEIVED",
        "PAID_OUT,              REFUND_INITIATED",
        "CHARGEBACK_RECEIVED,   CHARGEBACK_CONTESTED",
        "CHARGEBACK_RECEIVED,   CHARGEBACK_LOST",
        "CHARGEBACK_CONTESTED,  CHARGEBACK_EVIDENCE_SUBMITTED",
        "CHARGEBACK_EVIDENCE_SUBMITTED, CHARGEBACK_WON",
        "CHARGEBACK_EVIDENCE_SUBMITTED, CHARGEBACK_LOST"
    })
    void validTransitionReturnsTrue(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "INITIATED,          AUTHORIZED",
        "INITIATED,          CAPTURED",
        "AUTHORIZED,         INITIATED",
        "AUTHORIZED,         DECLINED",
        "CAPTURED,           AUTHORIZED",
        "CAPTURED,           REVERSAL_PENDING",
        "DECLINED,           INITIATED",
        "DECLINED,           AUTHORIZED",
        "REVERSED,           AUTHORIZED",
        "SETTLEMENT_PENDING, CAPTURED",
        "RECONCILED,         SETTLEMENT_PENDING",
        "CHARGEBACK_WON,     CHARGEBACK_RECEIVED",
        "CHARGEBACK_LOST,    CHARGEBACK_RECEIVED"
    })
    void invalidTransitionReturnsFalse(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void terminalStatesHaveNoValidTransitions() {
        assertThat(DECLINED.canTransitionTo(INITIATED)).isFalse();
        assertThat(REVERSED.canTransitionTo(INITIATED)).isFalse();
        assertThat(REFUNDED.canTransitionTo(INITIATED)).isFalse();
        assertThat(REFUND_FAILED.canTransitionTo(INITIATED)).isFalse();
        assertThat(PAID_OUT.canTransitionTo(INITIATED)).isFalse();
        assertThat(CHARGEBACK_WON.canTransitionTo(INITIATED)).isFalse();
        assertThat(CHARGEBACK_LOST.canTransitionTo(INITIATED)).isFalse();
    }

    @Test
    void cannotTransitionToSameState() {
        for (TransactionStatus status : TransactionStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }
}
