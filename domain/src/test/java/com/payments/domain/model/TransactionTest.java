package com.payments.domain.model;

import com.payments.domain.model.event.DomainEvent;
import com.payments.domain.model.vo.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static com.payments.domain.model.TransactionStatus.*;
import static org.assertj.core.api.Assertions.*;

class TransactionTest {

    private static final Currency INR = Currency.getInstance("INR");

    private Transaction sampleTransaction() {
        return Transaction.builder()
            .id(UUID.randomUUID())
            .merchantId(MerchantId.of("MERCH0000999"))
            .terminalId(TerminalId.of("TERM0042"))
            .amount(Money.of("6000.00", INR))
            .network(PaymentNetwork.VISA)
            .paymentMethod(PaymentMethod.CARD_CHIP)
            .panHash(PanHash.of("a".repeat(64)))
            .stan(SystemTraceAuditNumber.of("000042"))
            .status(INITIATED)
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void buildsTransactionWithRequiredFields() {
        Transaction txn = sampleTransaction();
        assertThat(txn.id()).isNotNull();
        assertThat(txn.merchantId().value()).isEqualTo("MERCH0000999");
        assertThat(txn.status()).isEqualTo(INITIATED);
    }

    @Test
    void withStatusReturnsNewInstanceWithUpdatedStatus() {
        Transaction original = sampleTransaction();
        Transaction updated = original.withStatus(AUTHORIZATION_PENDING);

        assertThat(updated.status()).isEqualTo(AUTHORIZATION_PENDING);
        assertThat(original.status()).isEqualTo(INITIATED);
        assertThat(updated.id()).isEqualTo(original.id());
    }

    @Test
    void withAuthCodeReturnsNewInstanceWithAuthCode() {
        Transaction txn = sampleTransaction().withStatus(AUTHORIZED);
        AuthorizationCode code = AuthorizationCode.of("ABC123");
        Transaction withCode = txn.withAuthCode(code);

        assertThat(withCode.authorizationCode()).isEqualTo(code);
        assertThat(txn.authorizationCode()).isNull();
    }

    @Test
    void withArnReturnsNewInstance() {
        Transaction txn = sampleTransaction();
        AcquirerReferenceNumber arn = AcquirerReferenceNumber.of("12345678901234567890123");
        Transaction withArn = txn.withArn(arn);

        assertThat(withArn.arn()).isEqualTo(arn);
        assertThat(txn.arn()).isNull();
    }

    @Test
    void raiseDomainEventAddsToList() {
        Transaction txn = sampleTransaction();
        assertThat(txn.domainEvents()).isEmpty();

        txn.raiseEvent(DomainEvent.of("transaction.initiated", txn.id().toString(), "TRANSACTION", INITIATED));
        assertThat(txn.domainEvents()).hasSize(1);
    }

    @Test
    void pullDomainEventsClearsTheList() {
        Transaction txn = sampleTransaction();
        txn.raiseEvent(DomainEvent.of("transaction.initiated", txn.id().toString(), "TRANSACTION", INITIATED));
        txn.raiseEvent(DomainEvent.of("transaction.authorization_pending", txn.id().toString(), "TRANSACTION", AUTHORIZATION_PENDING));

        List<DomainEvent<?>> events = txn.pullDomainEvents();
        assertThat(events).hasSize(2);
        assertThat(txn.domainEvents()).isEmpty();
    }

    @Test
    void immutableFieldsUnchangedAcrossWithStatusCalls() {
        Transaction original = sampleTransaction();
        Transaction updated = original.withStatus(AUTHORIZATION_PENDING)
                                      .withStatus(AUTHORIZED);

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.merchantId()).isEqualTo(original.merchantId());
        assertThat(updated.amount()).isEqualTo(original.amount());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    void throwsWhenIdIsNull() {
        assertThatThrownBy(() ->
            Transaction.builder()
                .merchantId(MerchantId.of("MERCH0000999"))
                .terminalId(TerminalId.of("TERM0042"))
                .amount(Money.of("6000.00", INR))
                .network(PaymentNetwork.VISA)
                .paymentMethod(PaymentMethod.CARD_CHIP)
                .panHash(PanHash.of("a".repeat(64)))
                .stan(SystemTraceAuditNumber.of("000042"))
                .status(INITIATED)
                .createdAt(Instant.now())
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenStatusIsNull() {
        assertThatThrownBy(() ->
            Transaction.builder()
                .id(UUID.randomUUID())
                .merchantId(MerchantId.of("MERCH0000999"))
                .terminalId(TerminalId.of("TERM0042"))
                .amount(Money.of("6000.00", INR))
                .network(PaymentNetwork.VISA)
                .paymentMethod(PaymentMethod.CARD_CHIP)
                .panHash(PanHash.of("a".repeat(64)))
                .stan(SystemTraceAuditNumber.of("000042"))
                .status(null)
                .createdAt(Instant.now())
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
