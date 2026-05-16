package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.vo.*;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

class QRGenerationCommandTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void valid_command_constructs_successfully() {
        assertThatNoException().isThrownBy(() ->
                new QRGenerationCommand(
                        MerchantId.of("MERCH0000999"),
                        TerminalId.of("TERM0042"),
                        Money.of("500.00", INR),
                        "ORDER-001"
                ));
    }

    @Test
    void null_merchantId_throws() {
        assertThatThrownBy(() -> new QRGenerationCommand(
                null, TerminalId.of("TERM0042"), Money.of("500.00", INR), "ORDER-001"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_terminalId_throws() {
        assertThatThrownBy(() -> new QRGenerationCommand(
                MerchantId.of("MERCH0000999"), null, Money.of("500.00", INR), "ORDER-001"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blank_orderId_throws() {
        assertThatThrownBy(() -> new QRGenerationCommand(
                MerchantId.of("MERCH0000999"), TerminalId.of("TERM0042"), Money.of("500.00", INR), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    void zero_amount_throws() {
        assertThatThrownBy(() -> new QRGenerationCommand(
                MerchantId.of("MERCH0000999"), TerminalId.of("TERM0042"), Money.of("0.00", INR), "ORDER-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }
}
