package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.*;
import com.nexswitch.domain.model.vo.*;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AuthorizationCommandTest {

    private static final Currency INR = Currency.getInstance("INR");

    private AuthorizationCommand valid() {
        return new AuthorizationCommand(
                UUID.randomUUID(),
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                "411111",
                PanHash.of("a".repeat(64)),
                Money.of("6000.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                null,
                new byte[]{0x02},
                null,   // ksn: null for non-PIN flows
                "07",
                "1111", // cardLast4
                null,   // cavv: null for non-3DS flows
                null    // eci:  null for non-3DS flows
        );
    }

    @Test
    void valid_command_constructs_successfully() {
        assertThatNoException().isThrownBy(this::valid);
    }

    @Test
    void null_transactionId_throws() {
        assertThatThrownBy(() -> new AuthorizationCommand(
                null,
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                "411111",
                PanHash.of("a".repeat(64)),
                Money.of("6000.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                null,
                new byte[]{0x02},
                null,
                "07",
                "1111",
                null, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_merchantId_throws() {
        assertThatThrownBy(() -> new AuthorizationCommand(
                UUID.randomUUID(),
                null,
                TerminalId.of("TERM0042"),
                "411111",
                PanHash.of("a".repeat(64)),
                Money.of("6000.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                null,
                new byte[]{0x02},
                null,
                "07",
                "1111",
                null, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void zero_amount_throws() {
        assertThatThrownBy(() -> new AuthorizationCommand(
                UUID.randomUUID(),
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                "411111",
                PanHash.of("a".repeat(64)),
                Money.of("0.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                null,
                new byte[]{0x02},
                null,
                "07",
                "1111",
                null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }

    @Test
    void negative_amount_throws() {
        assertThatThrownBy(() -> new AuthorizationCommand(
                UUID.randomUUID(),
                MerchantId.of("MERCH0000999"),
                TerminalId.of("TERM0042"),
                "411111",
                PanHash.of("a".repeat(64)),
                Money.of("-1.00", INR),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                SystemTraceAuditNumber.of("000042"),
                null,
                new byte[]{0x02},
                null,
                "07",
                "1111",
                null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
