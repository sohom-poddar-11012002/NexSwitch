package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.AuthorizationCode;

import java.time.Instant;

// LEARN: SealedInterface — compiler enforces exhaustive switch; no default case = no missed outcome
public sealed interface AuthorizationResult
        permits AuthorizationResult.Approved,
                AuthorizationResult.Declined,
                AuthorizationResult.Unknown,
                AuthorizationResult.Blocked {

    // arpc: null for non-chip (contactless/QR); 8-byte ARPC Method 1 for EMV chip — echoed in Field 91
    record Approved(AuthorizationCode authCode, Instant authorizedAt, byte[] arpc) implements AuthorizationResult {}

    record Declined(String responseCode, String reason) implements AuthorizationResult {}

    record Unknown(String reason, boolean reversalSent) implements AuthorizationResult {}

    record Blocked(String fraudRule) implements AuthorizationResult {}
}
