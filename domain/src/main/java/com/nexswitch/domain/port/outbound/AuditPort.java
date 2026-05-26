package com.nexswitch.domain.port.outbound;

import java.util.UUID;

// LEARN: AppendOnlyAudit — domain calls record(); adapter writes to audit_log (UPDATE/DELETE revoked at DB level)
public interface AuditPort {
    void record(String eventType,
                String actorService,
                UUID transactionId,
                String aggregateId,
                String aggregateType,
                String previousState,
                String newState,
                String eventDataJson);
}
