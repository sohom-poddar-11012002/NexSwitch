package com.nexswitch.domain.model.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class NpciTxnIdTest {

    @Test
    void constructsWithValidValue() {
        NpciTxnId id = new NpciTxnId("NPCI9999000111ABC");
        assertThat(id.value()).isEqualTo("NPCI9999000111ABC");
    }

    @Test
    void toStringReturnsValue() {
        assertThat(new NpciTxnId("NPCI001").toString()).isEqualTo("NPCI001");
    }

    @Test
    void ofFactoryMethodWorks() {
        assertThat(NpciTxnId.of("NPCI999").value()).isEqualTo("NPCI999");
    }

    @Test
    void throwsOnNull() {
        assertThatThrownBy(() -> new NpciTxnId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnBlank() {
        assertThatThrownBy(() -> new NpciTxnId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityBasedOnValue() {
        assertThat(new NpciTxnId("NPCI001")).isEqualTo(new NpciTxnId("NPCI001"));
        assertThat(new NpciTxnId("NPCI001")).isNotEqualTo(new NpciTxnId("NPCI002"));
    }
}
