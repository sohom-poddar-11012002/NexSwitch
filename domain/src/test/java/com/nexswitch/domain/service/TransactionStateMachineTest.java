package com.nexswitch.domain.service;

import com.nexswitch.domain.exception.InvalidStateTransitionException;
import com.nexswitch.domain.fixture.TransactionFixture;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.nexswitch.domain.model.TransactionStatus.*;
import static org.assertj.core.api.Assertions.*;

class TransactionStateMachineTest {

    private TransactionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TransactionStateMachine();
    }

    // ── Valid transitions — all 30 from CLAUDE.md §9.2 ──────────────────────

    @ParameterizedTest
    @CsvSource({
        "INITIATED,                       AUTHORIZATION_PENDING",
        "INITIATED,                       DECLINED",
        "AUTHORIZATION_PENDING,           AUTHORIZED",
        "AUTHORIZATION_PENDING,           DECLINED",
        "AUTHORIZATION_PENDING,           REVERSAL_PENDING",
        "AUTHORIZATION_PENDING,           UNKNOWN",
        "AUTHORIZED,                      CAPTURED",
        "AUTHORIZED,                      REVERSAL_PENDING",
        "AUTHORIZED,                      REFUND_INITIATED",
        "CAPTURED,                        SETTLEMENT_PENDING",
        "CAPTURED,                        REFUND_INITIATED",
        "REVERSAL_PENDING,                REVERSED",
        "REVERSAL_PENDING,                UNKNOWN",
        "UNKNOWN,                         AUTHORIZED",
        "UNKNOWN,                         REVERSED",
        "UNKNOWN,                         DECLINED",
        "REFUND_INITIATED,                REFUND_PENDING",
        "REFUND_INITIATED,                REFUND_FAILED",
        "REFUND_PENDING,                  REFUNDED",
        "REFUND_PENDING,                  REFUND_FAILED",
        "SETTLEMENT_PENDING,              RECONCILED",
        "RECONCILED,                      PAID_OUT",
        "RECONCILED,                      CHARGEBACK_RECEIVED",
        "PAID_OUT,                        CHARGEBACK_RECEIVED",
        "PAID_OUT,                        REFUND_INITIATED",
        "CHARGEBACK_RECEIVED,             CHARGEBACK_CONTESTED",
        "CHARGEBACK_RECEIVED,             CHARGEBACK_LOST",
        "CHARGEBACK_CONTESTED,            CHARGEBACK_EVIDENCE_SUBMITTED",
        "CHARGEBACK_EVIDENCE_SUBMITTED,   CHARGEBACK_WON",
        "CHARGEBACK_EVIDENCE_SUBMITTED,   CHARGEBACK_LOST"
    })
    void validTransitionUpdatesStatus(TransactionStatus from, TransactionStatus to) {
        Transaction txn = TransactionFixture.withStatus(from);

        Transaction result = stateMachine.transition(txn, to);

        assertThat(result.status()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
        "INITIATED,                       AUTHORIZATION_PENDING",
        "INITIATED,                       DECLINED",
        "AUTHORIZATION_PENDING,           AUTHORIZED",
        "AUTHORIZED,                      CAPTURED",
        "CAPTURED,                        SETTLEMENT_PENDING",
        "REVERSAL_PENDING,                REVERSED",
        "UNKNOWN,                         AUTHORIZED",
        "REFUND_INITIATED,                REFUND_PENDING",
        "REFUND_PENDING,                  REFUNDED",
        "SETTLEMENT_PENDING,              RECONCILED",
        "RECONCILED,                      PAID_OUT",
        "CHARGEBACK_RECEIVED,             CHARGEBACK_CONTESTED",
        "CHARGEBACK_EVIDENCE_SUBMITTED,   CHARGEBACK_WON"
    })
    void validTransitionRaisesDomainEvent(TransactionStatus from, TransactionStatus to) {
        Transaction txn = TransactionFixture.withStatus(from);

        Transaction result = stateMachine.transition(txn, to);

        String expectedEventType = "transaction." + to.name().toLowerCase();
        assertThat(result.domainEvents())
            .extracting(DomainEvent::eventType)
            .containsExactly(expectedEventType);
    }

    @Test
    void transitionDoesNotMutateOriginalTransaction() {
        Transaction original = TransactionFixture.withStatus(INITIATED);

        stateMachine.transition(original, AUTHORIZATION_PENDING);

        assertThat(original.status()).isEqualTo(INITIATED);
        assertThat(original.domainEvents()).isEmpty();
    }

    @Test
    void transitionPreservesAllOtherFields() {
        Transaction original = TransactionFixture.withStatus(INITIATED);

        Transaction result = stateMachine.transition(original, AUTHORIZATION_PENDING);

        assertThat(result.id()).isEqualTo(original.id());
        assertThat(result.merchantId()).isEqualTo(original.merchantId());
        assertThat(result.amount()).isEqualTo(original.amount());
        assertThat(result.createdAt()).isEqualTo(original.createdAt());
    }

    // ── Invalid transitions — throw InvalidStateTransitionException ──────────

    @ParameterizedTest
    @CsvSource({
        "INITIATED,          AUTHORIZED",
        "INITIATED,          CAPTURED",
        "AUTHORIZED,         INITIATED",
        "AUTHORIZED,         DECLINED",
        "CAPTURED,           REVERSAL_PENDING",
        "DECLINED,           AUTHORIZED",
        "REVERSED,           AUTHORIZED",
        "SETTLEMENT_PENDING, CAPTURED",
        "RECONCILED,         SETTLEMENT_PENDING",
        "CHARGEBACK_WON,     CHARGEBACK_RECEIVED",
        "CHARGEBACK_LOST,    CHARGEBACK_RECEIVED",
        "REFUNDED,           REFUND_INITIATED",
        "REFUND_FAILED,      REFUND_PENDING"
    })
    void invalidTransitionThrowsException(TransactionStatus from, TransactionStatus to) {
        Transaction txn = TransactionFixture.withStatus(from);

        assertThatThrownBy(() -> stateMachine.transition(txn, to))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void exceptionCarriesFromAndToStatus() {
        Transaction txn = TransactionFixture.withStatus(DECLINED);

        assertThatThrownBy(() -> stateMachine.transition(txn, AUTHORIZED))
            .isInstanceOf(InvalidStateTransitionException.class)
            .satisfies(ex -> {
                InvalidStateTransitionException iste = (InvalidStateTransitionException) ex;
                assertThat(iste.from()).isEqualTo(DECLINED);
                assertThat(iste.to()).isEqualTo(AUTHORIZED);
            });
    }

    @Test
    void exceptionCarriesTransactionId() {
        Transaction txn = TransactionFixture.withStatus(DECLINED);

        assertThatThrownBy(() -> stateMachine.transition(txn, AUTHORIZED))
            .isInstanceOf(InvalidStateTransitionException.class)
            .satisfies(ex -> {
                InvalidStateTransitionException iste = (InvalidStateTransitionException) ex;
                assertThat(iste.transactionId()).isEqualTo(txn.id());
            });
    }

    @Test
    void exceptionMessageContainsStatusNames() {
        Transaction txn = TransactionFixture.withStatus(DECLINED);

        assertThatThrownBy(() -> stateMachine.transition(txn, AUTHORIZED))
            .hasMessageContaining("DECLINED")
            .hasMessageContaining("AUTHORIZED");
    }

    // ── Self-transitions are invalid ─────────────────────────────────────────

    @Test
    void selfTransitionThrows() {
        Transaction txn = TransactionFixture.withStatus(AUTHORIZED);

        assertThatThrownBy(() -> stateMachine.transition(txn, AUTHORIZED))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Terminal states have no valid outbound transitions ───────────────────

    @ParameterizedTest
    @CsvSource({
        "DECLINED",
        "REVERSED",
        "REFUNDED",
        "REFUND_FAILED",
        "CHARGEBACK_WON",
        "CHARGEBACK_LOST"
    })
    void terminalStatesCannotTransitionToAnything(TransactionStatus terminal) {
        Transaction txn = TransactionFixture.withStatus(terminal);

        assertThatThrownBy(() -> stateMachine.transition(txn, INITIATED))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Null guards ──────────────────────────────────────────────────────────

    @Test
    void nullTransactionThrows() {
        assertThatThrownBy(() -> stateMachine.transition(null, AUTHORIZED))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTargetStatusThrows() {
        Transaction txn = TransactionFixture.withStatus(INITIATED);

        assertThatThrownBy(() -> stateMachine.transition(txn, null))
            .isInstanceOf(NullPointerException.class);
    }
}
