package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.vo.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RefundCommandTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void valid_command_constructs_successfully() {
        assertThatNoException().isThrownBy(() ->
                new RefundCommand(UUID.randomUUID(), Money.of("500.00", INR), "Customer request"));
    }

    @Test
    void null_transactionId_throws() {
        assertThatThrownBy(() -> new RefundCommand(null, Money.of("500.00", INR), "reason"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_amount_throws() {
        assertThatThrownBy(() -> new RefundCommand(UUID.randomUUID(), null, "reason"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blank_reason_throws() {
        assertThatThrownBy(() -> new RefundCommand(UUID.randomUUID(), Money.of("500.00", INR), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void zero_amount_throws() {
        assertThatThrownBy(() -> new RefundCommand(UUID.randomUUID(), Money.of("0.00", INR), "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refundAmount must be positive");
    }
}
