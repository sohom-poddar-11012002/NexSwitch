package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.CryptoKeyRegistryEntry;

import java.time.Duration;
import java.util.List;

// LEARN: Key rotation observability — findExpiringSoon() feeds a scheduled job that pages on-call
//        engineers before keys expire. A missed ZPK rotation causes ALL PIN-based transactions to fail.
public interface CryptoKeyRegistryPort {

    void register(CryptoKeyRegistryEntry entry);

    /** Returns entries whose expiresAt falls within the given duration from now. */
    List<CryptoKeyRegistryEntry> findExpiringSoon(Duration within);

    void markRotated(String keyAlias);
}
