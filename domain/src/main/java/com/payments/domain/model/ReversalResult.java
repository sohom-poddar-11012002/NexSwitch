package com.payments.domain.model;

import java.time.Instant;

// LEARN: Idempotency — AlreadyReversed permit is the domain's way of expressing "safe to retry"
public sealed interface ReversalResult
        permits ReversalResult.Accepted,
                ReversalResult.AlreadyReversed,
                ReversalResult.Failed {

    record Accepted(Instant reversedAt) implements ReversalResult {}

    record AlreadyReversed() implements ReversalResult {}

    record Failed(String reason) implements ReversalResult {}
}
