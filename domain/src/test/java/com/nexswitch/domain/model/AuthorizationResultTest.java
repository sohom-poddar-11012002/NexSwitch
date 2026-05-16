package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.AuthorizationCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class AuthorizationResultTest {

    @Test
    void approved_carries_authCode_and_timestamp() {
        var authCode = AuthorizationCode.of("483921");
        var now = Instant.now();
        var result = new AuthorizationResult.Approved(authCode, now);
        assertThat(result.authCode()).isEqualTo(authCode);
        assertThat(result.authorizedAt()).isEqualTo(now);
    }

    @Test
    void declined_carries_responseCode_and_reason() {
        var result = new AuthorizationResult.Declined("51", "Insufficient funds");
        assertThat(result.responseCode()).isEqualTo("51");
        assertThat(result.reason()).isEqualTo("Insufficient funds");
    }

    @Test
    void unknown_carries_reason_and_reversalSent_flag() {
        var result = new AuthorizationResult.Unknown("Timeout", true);
        assertThat(result.reason()).isEqualTo("Timeout");
        assertThat(result.reversalSent()).isTrue();
    }

    @Test
    void blocked_carries_fraudRule() {
        var result = new AuthorizationResult.Blocked("PAN_VELOCITY");
        assertThat(result.fraudRule()).isEqualTo("PAN_VELOCITY");
    }

    @Test
    void pattern_match_is_exhaustive_for_all_permits() {
        AuthorizationResult result = new AuthorizationResult.Approved(
                AuthorizationCode.of("000001"), Instant.now());
        String label = switch (result) {
            case AuthorizationResult.Approved a  -> "approved";
            case AuthorizationResult.Declined d  -> "declined";
            case AuthorizationResult.Unknown u   -> "unknown";
            case AuthorizationResult.Blocked b   -> "blocked";
        };
        assertThat(label).isEqualTo("approved");
    }
}
