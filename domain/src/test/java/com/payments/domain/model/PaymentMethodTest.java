package com.payments.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentMethodTest {

    @Test
    void enumHasFourValues() {
        assertThat(PaymentMethod.values()).hasSize(4);
    }

    @Test
    void containsExpectedValues() {
        assertThat(PaymentMethod.values()).containsExactlyInAnyOrder(
            PaymentMethod.CARD_CHIP,
            PaymentMethod.CONTACTLESS,
            PaymentMethod.UPI_QR,
            PaymentMethod.UPI_COLLECT
        );
    }

    @Test
    void cardMethodsAreCardBased() {
        assertThat(PaymentMethod.CARD_CHIP.isCard()).isTrue();
        assertThat(PaymentMethod.CONTACTLESS.isCard()).isTrue();
        assertThat(PaymentMethod.UPI_QR.isCard()).isFalse();
        assertThat(PaymentMethod.UPI_COLLECT.isCard()).isFalse();
    }

    @Test
    void upiMethodsAreUpi() {
        assertThat(PaymentMethod.UPI_QR.isUpi()).isTrue();
        assertThat(PaymentMethod.UPI_COLLECT.isUpi()).isTrue();
        assertThat(PaymentMethod.CARD_CHIP.isUpi()).isFalse();
        assertThat(PaymentMethod.CONTACTLESS.isUpi()).isFalse();
    }
}
