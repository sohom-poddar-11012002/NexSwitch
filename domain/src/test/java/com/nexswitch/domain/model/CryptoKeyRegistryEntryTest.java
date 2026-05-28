package com.nexswitch.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CryptoKeyRegistryEntryTest {

    private CryptoKeyRegistryEntry valid() {
        return new CryptoKeyRegistryEntry(
                UUID.randomUUID(), "ZPK", "payments-zpk",
                1, Instant.now(), Instant.now().plus(Duration.ofDays(365)),
                null, "ACTIVE"
        );
    }

    @Test
    void valid_entry_constructs_successfully() {
        assertThatNoException().isThrownBy(this::valid);
    }

    @Test
    void isActive_returns_true_for_ACTIVE_status() {
        assertThat(valid().isActive()).isTrue();
    }

    @Test
    void isActive_returns_false_for_ROTATED_status() {
        CryptoKeyRegistryEntry rotated = new CryptoKeyRegistryEntry(
                UUID.randomUUID(), "ZPK", "payments-zpk-old",
                1, Instant.now(), Instant.now().plus(Duration.ofDays(365)),
                Instant.now(), "ROTATED"
        );
        assertThat(rotated.isActive()).isFalse();
    }

    @Test
    void isExpiringSoon_true_when_expiry_within_threshold() {
        CryptoKeyRegistryEntry expiringSoon = new CryptoKeyRegistryEntry(
                UUID.randomUUID(), "ZPK", "payments-zpk-expiring",
                1, Instant.now(),
                Instant.now().plus(Duration.ofDays(1)),  // expires tomorrow
                null, "ACTIVE"
        );
        assertThat(expiringSoon.isExpiringSoon(Duration.ofDays(7))).isTrue();
    }

    @Test
    void null_keyType_throws() {
        assertThatThrownBy(() -> new CryptoKeyRegistryEntry(
                UUID.randomUUID(), null, "alias", null,
                Instant.now(), Instant.now().plus(Duration.ofDays(1)),
                null, "ACTIVE"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
