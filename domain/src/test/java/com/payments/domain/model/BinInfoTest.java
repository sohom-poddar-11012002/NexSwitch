package com.payments.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BinInfoTest {

    @Test
    void createsBinInfo() {
        BinInfo bin = new BinInfo("453914", PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN");
        assertThat(bin.binPrefix()).isEqualTo("453914");
        assertThat(bin.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(bin.issuerName()).isEqualTo("HDFC Bank");
    }

    @Test
    void throwsWhenBinPrefixIsNull() {
        assertThatThrownBy(() -> new BinInfo(null, PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNetworkIsNull() {
        assertThatThrownBy(() -> new BinInfo("453914", null, "HDFC Bank", "CREDIT", "SIGNATURE", "IN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void binPrefixMustBeSixToEightChars() {
        assertThatThrownBy(() -> new BinInfo("4539", PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new BinInfo("453914", PaymentNetwork.VISA, "HDFC", "CREDIT", "STANDARD", "IN"))
            .doesNotThrowAnyException();
        assertThatCode(() -> new BinInfo("45391480", PaymentNetwork.VISA, "HDFC", "CREDIT", "STANDARD", "IN"))
            .doesNotThrowAnyException();
    }
}
