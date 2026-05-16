package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SettlementBatchTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void createsSettlementBatch() {
        SettlementBatch batch = new SettlementBatch(
            UUID.randomUUID(),
            PaymentNetwork.VISA,
            LocalDate.of(2026, 5, 11),
            1247,
            Money.of("7824600.00", INR),
            SettlementBatch.Status.GENERATED
        );

        assertThat(batch.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(batch.transactionCount()).isEqualTo(1247);
        assertThat(batch.status()).isEqualTo(SettlementBatch.Status.GENERATED);
    }

    @Test
    void statusEnumHasExpectedValues() {
        assertThat(SettlementBatch.Status.values()).containsExactlyInAnyOrder(
            SettlementBatch.Status.GENERATED,
            SettlementBatch.Status.VALIDATED,
            SettlementBatch.Status.SUBMITTED,
            SettlementBatch.Status.ACKNOWLEDGED,
            SettlementBatch.Status.FAILED
        );
    }

    @Test
    void throwsWhenTransactionCountIsNegative() {
        assertThatThrownBy(() -> new SettlementBatch(
            UUID.randomUUID(), PaymentNetwork.VISA,
            LocalDate.now(), -1,
            Money.of("1000.00", INR), SettlementBatch.Status.GENERATED
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenBatchDateIsNull() {
        assertThatThrownBy(() -> new SettlementBatch(
            UUID.randomUUID(), PaymentNetwork.VISA,
            null, 100,
            Money.of("1000.00", INR), SettlementBatch.Status.GENERATED
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
