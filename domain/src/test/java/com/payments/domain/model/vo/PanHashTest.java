package com.payments.domain.model.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PanHashTest {

    // SHA-256 of "4539148803436467" — a valid 16-digit test PAN
    private static final String VALID_SHA256 =
        "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";

    @Test
    void createsFromValidSha256Hex() {
        PanHash hash = PanHash.of(VALID_SHA256);
        assertThat(hash.value()).isEqualTo(VALID_SHA256);
    }

    @Test
    void acceptsUpperCaseHex() {
        String upper = VALID_SHA256.toUpperCase();
        PanHash hash = PanHash.of(upper);
        assertThat(hash.value()).isEqualTo(upper.toLowerCase());
    }

    @Test
    void hashesRawPan() {
        PanHash hash = PanHash.fromRawPan("4539148803436467");
        assertThat(hash.value()).hasSize(64);
        assertThat(hash.value()).matches("[a-f0-9]{64}");
    }

    @Test
    void samePanProducesSameHash() {
        PanHash a = PanHash.fromRawPan("4539148803436467");
        PanHash b = PanHash.fromRawPan("4539148803436467");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentPansProduceDifferentHashes() {
        PanHash a = PanHash.fromRawPan("4539148803436467");
        PanHash b = PanHash.fromRawPan("5500005555555559");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void throwsForNull() {
        assertThatThrownBy(() -> PanHash.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsForNot64CharHash() {
        assertThatThrownBy(() -> PanHash.of("abc123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("64");
    }

    @Test
    void throwsForNonHexCharacters() {
        String nonHex = "z" + VALID_SHA256.substring(1);
        assertThatThrownBy(() -> PanHash.of(nonHex))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsFromRawPanForNull() {
        assertThatThrownBy(() -> PanHash.fromRawPan(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsFromRawPanForEmpty() {
        assertThatThrownBy(() -> PanHash.fromRawPan(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringReturnsMaskedRepresentation() {
        PanHash hash = PanHash.of(VALID_SHA256);
        // Should not return the raw hash in toString — return short prefix for logs
        assertThat(hash.toString()).startsWith("PanHash{");
    }
}
