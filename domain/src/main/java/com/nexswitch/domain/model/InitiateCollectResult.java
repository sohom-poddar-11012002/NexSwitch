package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.CollectId;

import java.time.Instant;

public sealed interface InitiateCollectResult
        permits InitiateCollectResult.Initiated, InitiateCollectResult.Failed {

    record Initiated(CollectId collectId, Instant expiresAt) implements InitiateCollectResult {}
    record Failed(String reason) implements InitiateCollectResult {}
}
