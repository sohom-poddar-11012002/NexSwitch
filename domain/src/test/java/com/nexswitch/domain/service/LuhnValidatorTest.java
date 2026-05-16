package com.nexswitch.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class LuhnValidatorTest {

    // ── Valid PANs ────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "4539148803436467",   // Visa 16-digit
        "4111111111111111",   // Visa test card
        "5500005555555559",   // Mastercard
        "371449635398431",    // Amex 15-digit
        "6011111111111117",   // Discover
        "3530111333300000",   // JCB
        "4539578763621486",   // Visa
        "4556737586899855"    // Visa
    })
    void validPan_returnsTrue(String pan) {
        assertThat(LuhnValidator.isValid(pan)).isTrue();
    }

    // ── Invalid PANs ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "4539148803436468",   // valid above with last digit off by 1
        "1234567890123456",   // random digits
        "4111111111111112"    // Visa test card, wrong check digit
    })
    void invalidCheckDigit_returnsFalse(String pan) {
        assertThat(LuhnValidator.isValid(pan)).isFalse();
    }

    // ── Null / blank / non-numeric guards ─────────────────────────────────────

    @Test
    void nullPan_returnsFalse() {
        assertThat(LuhnValidator.isValid(null)).isFalse();
    }

    @Test
    void blankPan_returnsFalse() {
        assertThat(LuhnValidator.isValid("   ")).isFalse();
    }

    @Test
    void nonNumericPan_returnsFalse() {
        assertThat(LuhnValidator.isValid("4539ABCD03436467")).isFalse();
    }

    @Test
    void emptyString_returnsFalse() {
        assertThat(LuhnValidator.isValid("")).isFalse();
    }

    // ── Length edge cases ─────────────────────────────────────────────────────

    @Test
    void singleDigit_returnsFalse() {
        assertThat(LuhnValidator.isValid("0")).isFalse();
    }

    @Test
    void tooShort_returnsFalse() {
        assertThat(LuhnValidator.isValid("411111")).isFalse();
    }

    // ── Spaces stripped ───────────────────────────────────────────────────────

    @Test
    void panWithSpaces_strippedAndValidated() {
        // "4111 1111 1111 1111" == "4111111111111111"
        assertThat(LuhnValidator.isValid("4111 1111 1111 1111")).isTrue();
    }
}
