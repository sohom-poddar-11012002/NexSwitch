package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.vo.*;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ReversalCommandTest {

    private static final Currency INR = Currency.getInstance("INR");

    private ReversalCommand valid() {
        return new ReversalCommand(
                UUID.randomUUID(),
                SystemTraceAuditNumber.of("000042"),
                Money.of("6000.00", INR),
                Money.of("6000.00", INR)
        );
    }

    @Test
    void valid_full_reversal_constructs_successfully() {
        assertThatNoException().isThrownBy(this::valid);
    }

    @Test
    void partial_reversal_within_original_amount_constructs_successfully() {
        assertThatNoException().isThrownBy(() -> new ReversalCommand(
                UUID.randomUUID(),
                SystemTraceAuditNumber.of("000042"),
                Money.of("10000.00", INR),
                Money.of("2000.00", INR)
        ));
    }

    @Test
    void null_transactionId_throws() {
        assertThatThrownBy(() -> new ReversalCommand(
                null,
                SystemTraceAuditNumber.of("000042"),
                Money.of("6000.00", INR),
                Money.of("6000.00", INR)
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void reversalAmount_exceeds_originalAmount_throws() {
        assertThatThrownBy(() -> new ReversalCommand(
                UUID.randomUUID(),
                SystemTraceAuditNumber.of("000042"),
                Money.of("1000.00", INR),
                Money.of("1001.00", INR)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reversalAmount must not exceed originalAmount");
    }

    @Test
    void zero_reversalAmount_throws() {
        assertThatThrownBy(() -> new ReversalCommand(
                UUID.randomUUID(),
                SystemTraceAuditNumber.of("000042"),
                Money.of("6000.00", INR),
                Money.of("0.00", INR)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reversalAmount must be positive");
    }
}
