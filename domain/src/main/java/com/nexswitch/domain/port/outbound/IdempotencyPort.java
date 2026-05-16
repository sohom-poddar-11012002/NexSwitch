package com.nexswitch.domain.port.outbound;

import java.time.Duration;

// LEARN: Idempotency — SETNX (SET if Not eXists) in Redis; the first caller receives true and owns
//        the key for the TTL window. Subsequent calls with the same key return false immediately.
//        ISO 8583 convention: key = terminalId + ":" + STAN uniquely identifies one terminal message.
//        This prevents double-processing if a terminal retransmits an unanswered request.
public interface IdempotencyPort {
    boolean acquire(String key, Duration ttl);
}
