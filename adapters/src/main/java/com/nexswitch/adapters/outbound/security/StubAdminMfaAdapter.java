package com.nexswitch.adapters.outbound.security;

import com.nexswitch.domain.port.outbound.AdminMfaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// LEARN: TOTP (RFC 6238) uses HMAC-SHA1 of (counter = floor(epoch/30)) — same algorithm as
//        Google Authenticator. The 30-second window tolerates clock skew between client and server.
//        Production wires a real TOTP library (e.g. java-otp); local profile accepts any code.
@Component
@Profile("local")
public class StubAdminMfaAdapter implements AdminMfaPort {

    private static final Logger log = LoggerFactory.getLogger(StubAdminMfaAdapter.class);

    // Pre-generated Base32 secret for local dev enrollment (32 chars = 160-bit key)
    private static final String STUB_SECRET = "JBSWY3DPEHPK3PXP";

    @Override
    public boolean verifyTotp(String username, String totpCode) {
        log.warn("admin.mfa.stub — TOTP verification bypassed in local profile. username={} code={}",
                username, totpCode);
        // LEARN: Stub always returns true — any 6-digit code is accepted for local dev.
        //        Production must replace this with a real HOTP/TOTP verification library.
        return true;
    }

    @Override
    public String generateSecret(String username) {
        log.info("admin.mfa.stub — returning static secret for local enrollment. username={}", username);
        return STUB_SECRET;
    }
}
