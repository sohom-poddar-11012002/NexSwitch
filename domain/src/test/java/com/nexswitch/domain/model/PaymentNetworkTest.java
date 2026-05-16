package com.nexswitch.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentNetworkTest {

    @Test
    void enumHasFourValues() {
        assertThat(PaymentNetwork.values()).hasSize(4);
    }

    @Test
    void containsExpectedValues() {
        assertThat(PaymentNetwork.values()).containsExactlyInAnyOrder(
            PaymentNetwork.VISA,
            PaymentNetwork.MASTERCARD,
            PaymentNetwork.RUPAY,
            PaymentNetwork.UPI
        );
    }

    @Test
    void upiNetworkIsUpi() {
        assertThat(PaymentNetwork.UPI.isUpi()).isTrue();
        assertThat(PaymentNetwork.VISA.isUpi()).isFalse();
        assertThat(PaymentNetwork.MASTERCARD.isUpi()).isFalse();
        assertThat(PaymentNetwork.RUPAY.isUpi()).isFalse();
    }

    @Test
    void internationalNetworks() {
        assertThat(PaymentNetwork.VISA.isInternational()).isTrue();
        assertThat(PaymentNetwork.MASTERCARD.isInternational()).isTrue();
        assertThat(PaymentNetwork.RUPAY.isInternational()).isFalse();
        assertThat(PaymentNetwork.UPI.isInternational()).isFalse();
    }
}
