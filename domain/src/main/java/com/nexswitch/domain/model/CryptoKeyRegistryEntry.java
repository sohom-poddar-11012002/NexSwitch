package com.nexswitch.domain.model;

import java.time.Instant;
import java.util.UUID;

// LEARN: Key lifecycle — ZMK wraps ZPK; ZPK encrypts PINs in transit; MAK signs ISO 8583 messages;
//        BDK is the Base Derivation Key that seeds DUKPT per-terminal unique keys.
//        Rotation must happen before expiry; a missed rotation locks all POS terminals.
public record CryptoKeyRegistryEntry(
        UUID id,
        String keyType,
        String keyAlias,
        Integer hsmSlot,
        Instant createdAt,
        Instant expiresAt,
        Instant rotatedAt,
        String status
) {
    public CryptoKeyRegistryEntry {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (keyType == null || keyType.isBlank()) throw new IllegalArgumentException("keyType must not be blank");
        if (keyAlias == null || keyAlias.isBlank()) throw new IllegalArgumentException("keyAlias must not be blank");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status must not be blank");
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isExpiringSoon(java.time.Duration within) {
        return isActive() && Instant.now().plus(within).isAfter(expiresAt);
    }
}
