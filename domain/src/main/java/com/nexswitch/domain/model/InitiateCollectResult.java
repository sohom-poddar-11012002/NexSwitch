package com.nexswitch.domain.model;

import java.time.Instant;

public sealed interface InitiateCollectResult
        permits InitiateCollectResult.Initiated, InitiateCollectResult.Failed {

    record Initiated(String collectId, Instant expiresAt) implements InitiateCollectResult {}
    record Failed(String reason) implements InitiateCollectResult {}
}
