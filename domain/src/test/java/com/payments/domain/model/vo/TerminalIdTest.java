package com.payments.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TerminalIdTest {

    @Test
    void createsValidTerminalId() {
        TerminalId id = TerminalId.of("TERM0042");
        assertThat(id.value()).isEqualTo("TERM0042");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TERM0042", "ABCD1234", "12345678"})
    void acceptsExactlyEightChars(String value) {
        assertThatNoException().isThrownBy(() -> TerminalId.of(value));
    }

    @Test
    void throwsForNull() {
        assertThatThrownBy(() -> TerminalId.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNot8Chars() {
        assertThatThrownBy(() -> TerminalId.of("TERM004"))  // 7 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("8");
    }

    @Test
    void throwsWhenTooLong() {
        assertThatThrownBy(() -> TerminalId.of("TERM00421"))  // 9 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("8");
    }

    @Test
    void throwsForSpecialCharacters() {
        assertThatThrownBy(() -> TerminalId.of("TERM-042"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityByValue() {
        TerminalId a = TerminalId.of("TERM0042");
        TerminalId b = TerminalId.of("TERM0042");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        assertThat(TerminalId.of("TERM0042").toString()).isEqualTo("TERM0042");
    }
}
