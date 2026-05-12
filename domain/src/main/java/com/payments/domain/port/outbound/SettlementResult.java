package com.payments.domain.port.outbound;

import java.time.Instant;

public sealed interface SettlementResult
        permits SettlementResult.Submitted,
                SettlementResult.Failed {

    record Submitted(String networkBatchId, Instant submittedAt) implements SettlementResult {}

    record Failed(String reason) implements SettlementResult {}
}
