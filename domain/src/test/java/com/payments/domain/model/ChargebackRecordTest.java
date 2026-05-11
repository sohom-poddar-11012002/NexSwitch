package com.payments.domain.model;

import com.payments.domain.model.vo.AcquirerReferenceNumber;
import com.payments.domain.model.vo.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ChargebackRecordTest {

    private static final Currency INR = Currency.getInstance("INR");

    private ChargebackRecord sample() {
        return new ChargebackRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            AcquirerReferenceNumber.of("12345678901234567890123"),
            PaymentNetwork.VISA,
            "4853",
            Money.of("6000.00", INR),
            Money.of("350.00", INR),
            ChargebackRecord.Status.RECEIVED,
            Instant.now().plusSeconds(30 * 24 * 3600),
            Instant.now()
        );
    }

    @Test
    void createsChargebackRecord() {
        ChargebackRecord record = sample();
        assertThat(record.id()).isNotNull();
        assertThat(record.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(record.reasonCode()).isEqualTo("4853");
        assertThat(record.status()).isEqualTo(ChargebackRecord.Status.RECEIVED);
    }

    @Test
    void totalLiabilityIsAmountPlusFee() {
        ChargebackRecord record = sample();
        assertThat(record.totalLiability().amount())
            .isEqualByComparingTo("6350.00");
    }

    @Test
    void statusEnumHasExpectedValues() {
        assertThat(ChargebackRecord.Status.values()).containsExactlyInAnyOrder(
            ChargebackRecord.Status.RECEIVED,
            ChargebackRecord.Status.ACCEPTED,
            ChargebackRecord.Status.CONTESTED,
            ChargebackRecord.Status.EVIDENCE_SUBMITTED,
            ChargebackRecord.Status.WON,
            ChargebackRecord.Status.LOST,
            ChargebackRecord.Status.WITHDRAWN
        );
    }

    @Test
    void throwsWhenTransactionIdIsNull() {
        assertThatThrownBy(() -> new ChargebackRecord(
            UUID.randomUUID(), null,
            AcquirerReferenceNumber.of("12345678901234567890123"),
            PaymentNetwork.VISA, "4853",
            Money.of("6000.00", INR), Money.of("350.00", INR),
            ChargebackRecord.Status.RECEIVED,
            Instant.now().plusSeconds(86400), Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
