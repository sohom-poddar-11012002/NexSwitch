package com.payments.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.payments.domain.model.TransactionStatus.*;
import static org.assertj.core.api.Assertions.*;

class TransactionStatusTest {

    @Test
    void enumHasTwentyStates() {
        assertThat(TransactionStatus.values()).hasSize(20);
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    void validTransitionReturnsTrue(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
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

    @SuppressWarnings("unused")
    static Stream<Object[]> validTransitions() {
        return Stream.of(
            new Object[]{INITIATED,             AUTHORIZATION_PENDING},
            new Object[]{INITIATED,             DECLINED},
            new Object[]{AUTHORIZATION_PENDING, AUTHORIZED},
            new Object[]{AUTHORIZATION_PENDING, DECLINED},
            new Object[]{AUTHORIZATION_PENDING, REVERSAL_PENDING},
            new Object[]{AUTHORIZATION_PENDING, UNKNOWN},
            new Object[]{AUTHORIZED,            CAPTURED},
            new Object[]{AUTHORIZED,            REVERSAL_PENDING},
            new Object[]{AUTHORIZED,            REFUND_INITIATED},
            new Object[]{CAPTURED,              SETTLEMENT_PENDING},
            new Object[]{CAPTURED,              REFUND_INITIATED},
            new Object[]{REVERSAL_PENDING,      REVERSED},
            new Object[]{REVERSAL_PENDING,      UNKNOWN},
            new Object[]{UNKNOWN,               AUTHORIZED},
            new Object[]{UNKNOWN,               REVERSED},
            new Object[]{UNKNOWN,               DECLINED},
            new Object[]{REFUND_INITIATED,      REFUND_PENDING},
            new Object[]{REFUND_INITIATED,      REFUND_FAILED},
            new Object[]{REFUND_PENDING,        REFUNDED},
            new Object[]{REFUND_PENDING,        REFUND_FAILED},
            new Object[]{SETTLEMENT_PENDING,    RECONCILED},
            new Object[]{RECONCILED,            PAID_OUT},
            new Object[]{RECONCILED,            CHARGEBACK_RECEIVED},
            new Object[]{PAID_OUT,              CHARGEBACK_RECEIVED},
            new Object[]{PAID_OUT,              REFUND_INITIATED},
            new Object[]{CHARGEBACK_RECEIVED,   CHARGEBACK_CONTESTED},
            new Object[]{CHARGEBACK_RECEIVED,   CHARGEBACK_LOST},
            new Object[]{CHARGEBACK_CONTESTED,  CHARGEBACK_EVIDENCE_SUBMITTED},
            new Object[]{CHARGEBACK_EVIDENCE_SUBMITTED, CHARGEBACK_WON},
            new Object[]{CHARGEBACK_EVIDENCE_SUBMITTED, CHARGEBACK_LOST}
        );
    }

    @SuppressWarnings("unused")
    static Stream<Object[]> invalidTransitions() {
        return Stream.of(
            new Object[]{INITIATED,             AUTHORIZED},
            new Object[]{INITIATED,             CAPTURED},
            new Object[]{AUTHORIZED,            INITIATED},
            new Object[]{AUTHORIZED,            DECLINED},
            new Object[]{CAPTURED,              AUTHORIZED},
            new Object[]{CAPTURED,              REVERSAL_PENDING},
            new Object[]{DECLINED,              INITIATED},
            new Object[]{DECLINED,              AUTHORIZED},
            new Object[]{REVERSED,              AUTHORIZED},
            new Object[]{SETTLEMENT_PENDING,    CAPTURED},
            new Object[]{RECONCILED,            SETTLEMENT_PENDING},
            new Object[]{CHARGEBACK_WON,        CHARGEBACK_RECEIVED},
            new Object[]{CHARGEBACK_LOST,       CHARGEBACK_RECEIVED}
        );
    }
}
