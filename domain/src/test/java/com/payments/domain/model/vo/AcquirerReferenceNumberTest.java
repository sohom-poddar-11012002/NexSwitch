package com.payments.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class AcquirerReferenceNumberTest {

    // ARN is a 23-character alphanumeric reference assigned by the acquirer
    private static final String VALID_ARN = "74491182312300000123456";

    @Test
    void createsValidArn() {
        AcquirerReferenceNumber arn = AcquirerReferenceNumber.of(VALID_ARN);
        assertThat(arn.value()).isEqualTo(VALID_ARN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "74491182312300000123456",   // 23 numeric
        "ABCDE123456789012345678"    // 23 alphanumeric
    })
    void acceptsValid23CharValues(String value) {
        assertThatNoException().isThrownBy(() -> AcquirerReferenceNumber.of(value));
    }

    @Test
    void throwsForNull() {
        assertThatThrownBy(() -> AcquirerReferenceNumber.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNot23Chars() {
        assertThatThrownBy(() -> AcquirerReferenceNumber.of("7449118231230000012345"))  // 22 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("23");
    }

    @Test
    void throwsWhenTooLong() {
        assertThatThrownBy(() -> AcquirerReferenceNumber.of("744911823123000001234561"))  // 24 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("23");
    }

    @Test
    void throwsForSpecialCharacters() {
        assertThatThrownBy(() -> AcquirerReferenceNumber.of("74491182312300000-23456"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityByValue() {
        AcquirerReferenceNumber a = AcquirerReferenceNumber.of(VALID_ARN);
        AcquirerReferenceNumber b = AcquirerReferenceNumber.of(VALID_ARN);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        assertThat(AcquirerReferenceNumber.of(VALID_ARN).toString()).isEqualTo(VALID_ARN);
    }
}
