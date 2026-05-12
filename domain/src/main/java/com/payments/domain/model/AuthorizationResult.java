package com.payments.domain.model;

import com.payments.domain.model.vo.AuthorizationCode;

import java.time.Instant;

public sealed interface AuthorizationResult
        permits AuthorizationResult.Approved,
                AuthorizationResult.Declined,
                AuthorizationResult.Unknown,
                AuthorizationResult.Blocked {

    record Approved(AuthorizationCode authCode, Instant authorizedAt) implements AuthorizationResult {}

    record Declined(String responseCode, String reason) implements AuthorizationResult {}

    record Unknown(String reason, boolean reversalSent) implements AuthorizationResult {}

    record Blocked(String fraudRule) implements AuthorizationResult {}
}
