package com.payments.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class AuthorizationCodeTest {

    @Test
    void createsValidAuthorizationCode() {
        AuthorizationCode code = AuthorizationCode.of("483921");
        assertThat(code.value()).isEqualTo("483921");
    }

    @ParameterizedTest
    @ValueSource(strings = {"483921", "ABC123", "ZZZZZZ", "000000"})
    void acceptsValidAlphanumericCodes(String value) {
        assertThatNoException().isThrownBy(() -> AuthorizationCode.of(value));
    }

    @Test
    void throwsForNull() {
        assertThatThrownBy(() -> AuthorizationCode.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNot6Chars() {
        assertThatThrownBy(() -> AuthorizationCode.of("48392"))  // 5 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("6");
    }

    @Test
    void throwsWhenTooLong() {
        assertThatThrownBy(() -> AuthorizationCode.of("4839211"))  // 7 chars
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("6");
    }

    @Test
    void throwsForSpecialCharacters() {
        assertThatThrownBy(() -> AuthorizationCode.of("483-21"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsForSpaces() {
        assertThatThrownBy(() -> AuthorizationCode.of("483 21"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storedAsUpperCase() {
        AuthorizationCode code = AuthorizationCode.of("abc123");
        assertThat(code.value()).isEqualTo("ABC123");
    }

    @Test
    void equalityByValue() {
        AuthorizationCode a = AuthorizationCode.of("483921");
        AuthorizationCode b = AuthorizationCode.of("483921");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        assertThat(AuthorizationCode.of("483921").toString()).isEqualTo("483921");
    }
}
