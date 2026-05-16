package com.payments.test.fixture;

import com.payments.domain.model.PaymentMethod;
import com.payments.domain.model.PaymentNetwork;
import com.payments.domain.model.Transaction;
import com.payments.domain.model.TransactionStatus;
import com.payments.domain.model.vo.*;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * Shared Transaction fixtures. All factory methods use the seeded V11 merchant/terminal
 * so integration tests run against data already present in the Testcontainers Postgres.
 *
 * Demo PAN 4539148803436467 (Luhn-valid Visa) from the demo script.
 */
// LEARN: TestFixture — hardcoded MERCHANT_ID/TERMINAL_ID match V11 seed; no INSERT needed in integration tests
public final class TransactionFixture {

    public static final Currency INR          = Currency.getInstance("INR");
    public static final String   MERCHANT_ID  = "MERCH0000999";
    public static final String   TERMINAL_ID  = "TERM0042";
    public static final String   TEST_PAN     = "4539148803436467";

    private TransactionFixture() {}

    /** INITIATED — card payment just received from terminal, not yet sent to network. */
    public static Transaction initiated() {
        return Transaction.initiate(
            UUID.randomUUID(),
            MerchantId.of(MERCHANT_ID),
            TerminalId.of(TERMINAL_ID),
            Money.of("6000.00", INR),
            PaymentNetwork.VISA,
            PaymentMethod.CARD_CHIP,
            PanHash.fromRawPan(TEST_PAN),
            SystemTraceAuditNumber.of("000001"),
            Instant.now()
        );
    }

    /** AUTHORIZED — network returned approval, auth code assigned. */
    public static Transaction authorized() {
        return initiated()
                .withStatus(TransactionStatus.AUTHORIZATION_PENDING)
                .authorize(AuthorizationCode.of("AUTH01"));
    }

    /** DECLINED — network returned code 05 (do not honour). */
    public static Transaction declined() {
        return initiated().decline("05");
    }

    /** REVERSAL_PENDING — timeout fired before network responded. */
    public static Transaction reversalPending() {
        return initiated()
                .withStatus(TransactionStatus.AUTHORIZATION_PENDING)
                .initiateReversal();
    }

    /** CHARGEBACK_RECEIVED — chargeback file received from network. */
    public static Transaction chargebackReceived() {
        return authorized()
                .withStatus(TransactionStatus.PAID_OUT)
                .receiveChargeback();
    }

    /**
     * Builder-style — use when a test needs a specific amount or network.
     *
     * Example:
     *   TransactionFixture.withAmount("500.00").withNetwork(PaymentNetwork.RUPAY).initiated()
     */
    public static FluentBuilder withAmount(String amount) {
        return new FluentBuilder().amount(amount);
    }

    public static FluentBuilder withNetwork(PaymentNetwork network) {
        return new FluentBuilder().network(network);
    }

    public static final class FluentBuilder {
        private String amount = "6000.00";
        private PaymentNetwork network = PaymentNetwork.VISA;
        private PaymentMethod method = PaymentMethod.CARD_CHIP;
        private String stan = "000001";

        public FluentBuilder amount(String amount)         { this.amount = amount;    return this; }
        public FluentBuilder network(PaymentNetwork n)     { this.network = n;        return this; }
        public FluentBuilder method(PaymentMethod m)       { this.method = m;         return this; }
        public FluentBuilder stan(String stan)             { this.stan = stan;        return this; }

        public Transaction initiated() {
            return Transaction.initiate(
                UUID.randomUUID(),
                MerchantId.of(MERCHANT_ID),
                TerminalId.of(TERMINAL_ID),
                Money.of(amount, INR),
                network,
                method,
                PanHash.fromRawPan(TEST_PAN),
                SystemTraceAuditNumber.of(stan),
                Instant.now()
            );
        }

        public Transaction authorized() {
            return initiated()
                    .withStatus(TransactionStatus.AUTHORIZATION_PENDING)
                    .authorize(AuthorizationCode.of("AUTH01"));
        }
    }
}
