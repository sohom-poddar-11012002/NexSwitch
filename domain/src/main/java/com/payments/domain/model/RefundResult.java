package com.payments.domain.model;

import java.time.Instant;
import java.util.UUID;

public sealed interface RefundResult
        permits RefundResult.Initiated,
                RefundResult.Failed {

    record Initiated(UUID refundId, Instant initiatedAt) implements RefundResult {}

    record Failed(String reason) implements RefundResult {}
}
