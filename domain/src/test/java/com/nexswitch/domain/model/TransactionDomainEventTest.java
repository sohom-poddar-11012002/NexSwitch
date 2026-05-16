package com.nexswitch.domain.model;

import com.nexswitch.domain.exception.InvalidStateTransitionException;
import com.nexswitch.domain.fixture.TransactionFixture;
import com.nexswitch.domain.model.event.*;
import com.nexswitch.domain.model.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static com.nexswitch.domain.model.TransactionStatus.*;
import static org.assertj.core.api.Assertions.*;

class TransactionDomainEventTest {

    private static final Currency INR = Currency.getInstance("INR");

    private Transaction authPending;
    private Transaction authorized;

    @BeforeEach
    void setUp() {
        authPending = TransactionFixture.withStatus(AUTHORIZATION_PENDING);
        authorized  = TransactionFixture.withStatus(AUTHORIZED);
    }

    // ── Transaction.initiate() static factory ─────────────────────────────────

    @Test
    void initiate_createsTransactionWithInitiatedStatus() {
        Transaction txn = buildViaInitiate();
        assertThat(txn.status()).isEqualTo(INITIATED);
    }

    @Test
    void initiate_raisesTransactionInitiatedEvent() {
        Transaction txn = buildViaInitiate();

        assertThat(txn.domainEvents()).hasSize(1);
        DomainEvent<?> event = txn.domainEvents().get(0);
        assertThat(event.eventType()).isEqualTo("transaction.initiated");
        assertThat(event.aggregateType()).isEqualTo("TRANSACTION");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.payload()).isInstanceOf(TransactionInitiatedEvent.class);
    }

    @Test
    void initiate_eventPayloadCarriesTransactionData() {
        UUID txnId = UUID.randomUUID();
        MerchantId merchantId = MerchantId.of("MERCH0000999");
        Money amount = Money.of("6000.00", INR);

        Transaction txn = Transaction.initiate(
            txnId, merchantId, TerminalId.of("TERM0042"), amount,
            PaymentNetwork.VISA, PaymentMethod.CARD_CHIP,
            PanHash.of("a".repeat(64)), SystemTraceAuditNumber.of("000042"),
            Instant.now()
        );

        TransactionInitiatedEvent payload =
            (TransactionInitiatedEvent) txn.domainEvents().get(0).payload();
        assertThat(payload.transactionId()).isEqualTo(txnId);
        assertThat(payload.merchantId()).isEqualTo(merchantId);
        assertThat(payload.amount()).isEqualTo(amount);
        assertThat(payload.network()).isEqualTo(PaymentNetwork.VISA);
    }

    // ── Transaction.authorize() ───────────────────────────────────────────────

    @Test
    void authorize_returnsTransactionWithAuthorizedStatus() {
        var authCode = AuthorizationCode.of("483921");
        Transaction result = authPending.authorize(authCode);
        assertThat(result.status()).isEqualTo(AUTHORIZED);
        assertThat(result.authorizationCode()).isEqualTo(authCode);
    }

    @Test
    void authorize_raisesTransactionAuthorizedEvent() {
        var authCode = AuthorizationCode.of("483921");
        Transaction result = authPending.authorize(authCode);

        assertThat(result.domainEvents()).hasSize(1);
        DomainEvent<?> event = result.domainEvents().get(0);
        assertThat(event.eventType()).isEqualTo("transaction.authorized");
        assertThat(event.aggregateId()).isEqualTo(authPending.id().toString());
        assertThat(event.payload()).isInstanceOf(TransactionAuthorizedEvent.class);
    }

    @Test
    void authorize_eventPayloadCarriesAuthCode() {
        var authCode = AuthorizationCode.of("483921");
        Transaction result = authPending.authorize(authCode);

        var payload = (TransactionAuthorizedEvent) result.domainEvents().get(0).payload();
        assertThat(payload.transactionId()).isEqualTo(authPending.id());
        assertThat(payload.authorizationCode()).isEqualTo(authCode);
        assertThat(payload.amount()).isEqualTo(authPending.amount());
        assertThat(payload.network()).isEqualTo(authPending.network());
    }

    @Test
    void authorize_doesNotMutateOriginal() {
        authPending.authorize(AuthorizationCode.of("483921"));
        assertThat(authPending.status()).isEqualTo(AUTHORIZATION_PENDING);
        assertThat(authPending.domainEvents()).isEmpty();
    }

    @Test
    void authorize_fromInvalidState_throwsInvalidStateTransitionException() {
        assertThatThrownBy(() -> authorized.authorize(AuthorizationCode.of("123456")))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Transaction.decline() ─────────────────────────────────────────────────

    @Test
    void decline_returnsTransactionWithDeclinedStatus() {
        Transaction result = authPending.decline("51");
        assertThat(result.status()).isEqualTo(DECLINED);
        assertThat(result.responseCode()).isEqualTo("51");
    }

    @Test
    void decline_raisesTransactionDeclinedEvent() {
        Transaction result = authPending.decline("51");

        assertThat(result.domainEvents()).hasSize(1);
        DomainEvent<?> event = result.domainEvents().get(0);
        assertThat(event.eventType()).isEqualTo("transaction.declined");
        assertThat(event.payload()).isInstanceOf(TransactionDeclinedEvent.class);
    }

    @Test
    void decline_eventPayloadCarriesResponseCode() {
        Transaction result = authPending.decline("05");

        var payload = (TransactionDeclinedEvent) result.domainEvents().get(0).payload();
        assertThat(payload.transactionId()).isEqualTo(authPending.id());
        assertThat(payload.responseCode()).isEqualTo("05");
    }

    @Test
    void decline_doesNotMutateOriginal() {
        authPending.decline("51");
        assertThat(authPending.status()).isEqualTo(AUTHORIZATION_PENDING);
        assertThat(authPending.domainEvents()).isEmpty();
    }

    @Test
    void decline_fromInvalidState_throwsInvalidStateTransitionException() {
        // AUTHORIZED cannot transition directly to DECLINED
        assertThatThrownBy(() -> authorized.decline("51"))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Transaction.initiateReversal() ───────────────────────────────────────

    @Test
    void initiateReversal_returnsTransactionWithReversalPendingStatus() {
        Transaction result = authPending.initiateReversal();
        assertThat(result.status()).isEqualTo(REVERSAL_PENDING);
    }

    @Test
    void initiateReversal_raisesReversalInitiatedEvent() {
        Transaction result = authPending.initiateReversal();

        assertThat(result.domainEvents()).hasSize(1);
        DomainEvent<?> event = result.domainEvents().get(0);
        assertThat(event.eventType()).isEqualTo("transaction.reversal_initiated");
        assertThat(event.payload()).isInstanceOf(ReversalInitiatedEvent.class);
    }

    @Test
    void initiateReversal_eventPayloadCarriesTransactionData() {
        Transaction result = authPending.initiateReversal();

        var payload = (ReversalInitiatedEvent) result.domainEvents().get(0).payload();
        assertThat(payload.transactionId()).isEqualTo(authPending.id());
        assertThat(payload.amount()).isEqualTo(authPending.amount());
    }

    @Test
    void initiateReversal_doesNotMutateOriginal() {
        authPending.initiateReversal();
        assertThat(authPending.status()).isEqualTo(AUTHORIZATION_PENDING);
        assertThat(authPending.domainEvents()).isEmpty();
    }

    @Test
    void initiateReversal_fromInvalidState_throwsInvalidStateTransitionException() {
        // INITIATED cannot go directly to REVERSAL_PENDING
        Transaction initiated = TransactionFixture.withStatus(INITIATED);
        assertThatThrownBy(initiated::initiateReversal)
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Transaction.receiveChargeback() ──────────────────────────────────────

    @Test
    void receiveChargeback_returnsTransactionWithChargebackReceivedStatus() {
        Transaction reconciled = TransactionFixture.withStatus(RECONCILED);
        Transaction result = reconciled.receiveChargeback();
        assertThat(result.status()).isEqualTo(CHARGEBACK_RECEIVED);
    }

    @Test
    void receiveChargeback_raisesChargebackReceivedEvent() {
        Transaction reconciled = TransactionFixture.withStatus(RECONCILED);
        Transaction result = reconciled.receiveChargeback();

        assertThat(result.domainEvents()).hasSize(1);
        DomainEvent<?> event = result.domainEvents().get(0);
        assertThat(event.eventType()).isEqualTo("transaction.chargeback_received");
        assertThat(event.payload()).isInstanceOf(ChargebackReceivedEvent.class);
    }

    @Test
    void receiveChargeback_eventPayloadCarriesTransactionData() {
        Transaction reconciled = TransactionFixture.withStatus(RECONCILED);
        Transaction result = reconciled.receiveChargeback();

        var payload = (ChargebackReceivedEvent) result.domainEvents().get(0).payload();
        assertThat(payload.transactionId()).isEqualTo(reconciled.id());
        assertThat(payload.amount()).isEqualTo(reconciled.amount());
    }

    @Test
    void receiveChargeback_doesNotMutateOriginal() {
        Transaction reconciled = TransactionFixture.withStatus(RECONCILED);
        reconciled.receiveChargeback();
        assertThat(reconciled.status()).isEqualTo(RECONCILED);
        assertThat(reconciled.domainEvents()).isEmpty();
    }

    @Test
    void receiveChargeback_fromInvalidState_throwsInvalidStateTransitionException() {
        assertThatThrownBy(authPending::receiveChargeback)
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── pullDomainEvents drains the list ──────────────────────────────────────

    @Test
    void pullDomainEvents_returnsRaisedEvents() {
        Transaction result = authPending.authorize(AuthorizationCode.of("483921"));
        assertThat(result.pullDomainEvents()).hasSize(1);
    }

    @Test
    void pullDomainEvents_clearsListAfterPull() {
        Transaction result = authPending.authorize(AuthorizationCode.of("483921"));
        result.pullDomainEvents();
        assertThat(result.domainEvents()).isEmpty();
    }

    @Test
    void pullDomainEvents_isNotIdempotent() {
        Transaction result = authPending.authorize(AuthorizationCode.of("483921"));
        assertThat(result.pullDomainEvents()).hasSize(1);
        assertThat(result.pullDomainEvents()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction buildViaInitiate() {
        return Transaction.initiate(
            UUID.randomUUID(),
            MerchantId.of("MERCH0000999"),
            TerminalId.of("TERM0042"),
            Money.of("6000.00", INR),
            PaymentNetwork.VISA,
            PaymentMethod.CARD_CHIP,
            PanHash.of("a".repeat(64)),
            SystemTraceAuditNumber.of("000042"),
            Instant.now()
        );
    }
}
