package com.nexswitch.domain.port.outbound;

// LEARN: TOTP (RFC 6238) — Time-Based One-Time Password uses HMAC-SHA1 of (counter = floor(epoch/30)).
//        The same algorithm powers Google Authenticator, Authy, and hardware TOTP tokens.
//        Secret is a Base32-encoded 160-bit random seed enrolled once per user.
public interface AdminMfaPort {

    /**
     * Verifies that the given TOTP code is valid for the user at the current 30-second window.
     * Returns true if valid; false otherwise.
     */
    boolean verifyTotp(String username, String totpCode);

    /**
     * Generates a new TOTP secret for the given user (enrollment step).
     * Returns a Base32-encoded secret that the user scans into their authenticator app.
     */
    String generateSecret(String username);
}
