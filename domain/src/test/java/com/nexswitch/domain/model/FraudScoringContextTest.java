package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.PanHash;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

class FraudScoringContextTest {

    private static final Currency INR = Currency.getInstance("INR");

    private FraudScoringContext valid() {
        return new FraudScoringContext(
                PanHash.of("a".repeat(64)),
                Money.of("6000.00", INR),
                "5411",
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                Instant.now()
        );
    }

    @Test
    void valid_context_constructs_successfully() {
        assertThatNoException().isThrownBy(this::valid);
    }

    @Test
    void null_panHash_throws() {
        assertThatThrownBy(() -> new FraudScoringContext(
                null, Money.of("6000.00", INR), "5411", PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_amount_throws() {
        assertThatThrownBy(() -> new FraudScoringContext(
                PanHash.of("a".repeat(64)), null, "5411", PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_merchantCategory_throws() {
        assertThatThrownBy(() -> new FraudScoringContext(
                PanHash.of("a".repeat(64)), Money.of("6000.00", INR), null, PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_network_throws() {
        assertThatThrownBy(() -> new FraudScoringContext(
                PanHash.of("a".repeat(64)), Money.of("6000.00", INR), "5411", null,
                PaymentMethod.CARD_CHIP, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_method_throws() {
        assertThatThrownBy(() -> new FraudScoringContext(
                PanHash.of("a".repeat(64)), Money.of("6000.00", INR), "5411", PaymentNetwork.VISA,
                null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_transactionTime_throws() {
        assertThatThrownBy(() -> new FraudScoringContext(
                PanHash.of("a".repeat(64)), Money.of("6000.00", INR), "5411", PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP, null))
                .isInstanceOf(NullPointerException.class);
    }
}
