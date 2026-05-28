package com.nexswitch.adapters.outbound.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class StubAdminMfaAdapterTest {

    private final StubAdminMfaAdapter adapter = new StubAdminMfaAdapter();

    @Test
    void verifyTotp_alwaysReturnsTrue_forAnyCode() {
        assertThat(adapter.verifyTotp("admin", "123456")).isTrue();
        assertThat(adapter.verifyTotp("admin", "000000")).isTrue();
        assertThat(adapter.verifyTotp("admin", "invalid")).isTrue();
    }

    @Test
    void generateSecret_returnsNonNullBase32String() {
        String secret = adapter.generateSecret("admin");

        assertThat(secret).isNotNull().isNotBlank();
        // STUB_SECRET is a Base32 string — only uppercase letters and digits 2–7
        assertThat(secret).matches("[A-Z2-7]+");
    }

    @Test
    void generateSecret_returnsSameSecretForAnyUsername() {
        String s1 = adapter.generateSecret("admin");
        String s2 = adapter.generateSecret("ops-user");

        // Stub always returns the same static secret regardless of username
        assertThat(s1).isEqualTo(s2);
    }
}
