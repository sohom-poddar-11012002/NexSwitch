package com.nexswitch.domain.model.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TxnRefTest {

    @Test
    void constructsWithValidValue() {
        TxnRef ref = new TxnRef("TXN20260511MERCH00001234");
        assertThat(ref.value()).isEqualTo("TXN20260511MERCH00001234");
    }

    @Test
    void toStringReturnsValue() {
        assertThat(new TxnRef("TXNABC").toString()).isEqualTo("TXNABC");
    }

    @Test
    void ofFactoryMethodWorks() {
        assertThat(TxnRef.of("TXNXYZ").value()).isEqualTo("TXNXYZ");
    }

    @Test
    void throwsOnNull() {
        assertThatThrownBy(() -> new TxnRef(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnBlank() {
        assertThatThrownBy(() -> new TxnRef(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityBasedOnValue() {
        assertThat(new TxnRef("TXNABC")).isEqualTo(new TxnRef("TXNABC"));
        assertThat(new TxnRef("TXNABC")).isNotEqualTo(new TxnRef("TXNXYZ"));
    }
}
