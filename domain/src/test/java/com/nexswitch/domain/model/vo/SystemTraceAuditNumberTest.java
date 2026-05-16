package com.nexswitch.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class SystemTraceAuditNumberTest {

    // STAN is a 6-digit numeric sequence number assigned by the terminal per transaction
    @Test
    void createsValidStan() {
        SystemTraceAuditNumber stan = SystemTraceAuditNumber.of("000001");
        assertThat(stan.value()).isEqualTo("000001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000001", "123456", "999999", "000000"})
    void acceptsValid6DigitValues(String value) {
        assertThatNoException().isThrownBy(() -> SystemTraceAuditNumber.of(value));
    }

    @Test
    void throwsForNull() {
        assertThatThrownBy(() -> SystemTraceAuditNumber.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNot6Digits() {
        assertThatThrownBy(() -> SystemTraceAuditNumber.of("12345"))  // 5 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("6");
    }

    @Test
    void throwsWhenTooLong() {
        assertThatThrownBy(() -> SystemTraceAuditNumber.of("1234567"))  // 7 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("6");
    }

    @Test
    void throwsForNonNumeric() {
        assertThatThrownBy(() -> SystemTraceAuditNumber.of("12A456"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("numeric");
    }

    @Test
    void throwsForNegativeSign() {
        assertThatThrownBy(() -> SystemTraceAuditNumber.of("-12345"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityByValue() {
        SystemTraceAuditNumber a = SystemTraceAuditNumber.of("000042");
        SystemTraceAuditNumber b = SystemTraceAuditNumber.of("000042");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        assertThat(SystemTraceAuditNumber.of("000042").toString()).isEqualTo("000042");
    }

    @Test
    void integerValueMatchesStringRepresentation() {
        SystemTraceAuditNumber stan = SystemTraceAuditNumber.of("000042");
        assertThat(stan.intValue()).isEqualTo(42);
    }
}
