package com.nexswitch.adapters.outbound.settlement;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.SettlementBatch;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.SettlementResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class StubSettlementPortAdapterTest {

    private static final Currency INR = Currency.getInstance("INR");
    private final StubSettlementPortAdapter adapter = new StubSettlementPortAdapter();

    @Test
    void submit_returnsSubmittedResult() {
        SettlementBatch batch = new SettlementBatch(
                UUID.randomUUID(), PaymentNetwork.VISA, LocalDate.now(),
                10, Money.of(new BigDecimal("60000.00"), INR),
                SettlementBatch.Status.GENERATED
        );

        SettlementResult result = adapter.submit(batch);

        assertThat(result).isInstanceOf(SettlementResult.Submitted.class);
    }

    @Test
    void submit_batchIdContainsDateAndStubSuffix() {
        LocalDate date = LocalDate.of(2026, 5, 29);
        SettlementBatch batch = new SettlementBatch(
                UUID.randomUUID(), PaymentNetwork.MASTERCARD, date,
                5, Money.of(new BigDecimal("10000.00"), INR),
                SettlementBatch.Status.GENERATED
        );

        SettlementResult result = adapter.submit(batch);

        SettlementResult.Submitted submitted = (SettlementResult.Submitted) result;
        assertThat(submitted.networkBatchId()).contains("2026-05-29");
        assertThat(submitted.networkBatchId()).contains("STUB");
        assertThat(submitted.submittedAt()).isNotNull();
    }
}
