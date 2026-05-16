package com.nexswitch.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BinInfoTest {

    @Test
    void createsBinInfo() {
        BinInfo bin = new BinInfo("453914", PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN", "HDFC", false);
        assertThat(bin.binPrefix()).isEqualTo("453914");
        assertThat(bin.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(bin.issuerName()).isEqualTo("HDFC Bank");
        assertThat(bin.issuerBank()).isEqualTo("HDFC");
        assertThat(bin.nfsEligible()).isFalse();
    }

    @Test
    void nfsEligibleTrueForRuPayCard() {
        BinInfo bin = new BinInfo("607080", PaymentNetwork.RUPAY, "SBI", "DEBIT", "CLASSIC", "IN", "SBI", true);
        assertThat(bin.nfsEligible()).isTrue();
        assertThat(bin.issuerBank()).isEqualTo("SBI");
    }

    @Test
    void throwsWhenBinPrefixIsNull() {
        assertThatThrownBy(() -> new BinInfo(null, PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN", "HDFC", false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNetworkIsNull() {
        assertThatThrownBy(() -> new BinInfo("453914", null, "HDFC Bank", "CREDIT", "SIGNATURE", "IN", "HDFC", false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void binPrefixMustBeSixToEightChars() {
        assertThatThrownBy(() -> new BinInfo("4539", PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN", "HDFC", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new BinInfo("453914", PaymentNetwork.VISA, "HDFC", "CREDIT", "STANDARD", "IN", "HDFC", false))
            .doesNotThrowAnyException();
        assertThatCode(() -> new BinInfo("45391480", PaymentNetwork.VISA, "HDFC", "CREDIT", "STANDARD", "IN", "HDFC", false))
            .doesNotThrowAnyException();
    }

    @Test
    void issuerBankNullAllowed() {
        assertThatCode(() -> new BinInfo("453914", PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "SIGNATURE", "IN", null, false))
            .doesNotThrowAnyException();
    }
}
