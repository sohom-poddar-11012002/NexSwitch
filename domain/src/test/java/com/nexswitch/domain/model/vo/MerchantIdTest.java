package com.nexswitch.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class MerchantIdTest {

    @Test
    void createsValidMerchantId() {
        MerchantId id = MerchantId.of("MERCH0000999");
        assertThat(id.value()).isEqualTo("MERCH0000999");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MERCH0000001", "ABCDE1234567890", "MERCH9999999"})
    void acceptsValidFormats(String value) {
        assertThatNoException().isThrownBy(() -> MerchantId.of(value));
    }

    @Test
    void throwsForNull() {
        assertThatThrownBy(() -> MerchantId.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsForEmptyString() {
        assertThatThrownBy(() -> MerchantId.of(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenTooLong() {
        assertThatThrownBy(() -> MerchantId.of("MERCH00000000001"))  // 16 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("15");
    }

    @Test
    void throwsWhenTooShort() {
        assertThatThrownBy(() -> MerchantId.of("MERCH"))  // 5 chars
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsForSpecialCharacters() {
        assertThatThrownBy(() -> MerchantId.of("MERCH000-9999"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityByValue() {
        MerchantId a = MerchantId.of("MERCH0000999");
        MerchantId b = MerchantId.of("MERCH0000999");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        assertThat(MerchantId.of("MERCH0000999").toString()).isEqualTo("MERCH0000999");
    }
}
