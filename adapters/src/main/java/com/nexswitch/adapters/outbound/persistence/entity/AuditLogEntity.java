package com.nexswitch.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// LEARN: AppendOnlyEntity — no @Version, no UPDATE; the DB revokes UPDATE/DELETE from app user (V3 migration)
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "actor_service", nullable = false, length = 100)
    private String actorService;

    @Column(name = "transaction_id", columnDefinition = "UUID")
    private UUID transactionId;

    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(name = "aggregate_type", length = 50)
    private String aggregateType;

    @Column(name = "previous_state", length = 50)
    private String previousState;

    @Column(name = "new_state", length = 50)
    private String newState;

    @Column(name = "event_data", nullable = false, columnDefinition = "jsonb")
    private String eventData;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AuditLogEntity() {}

    public AuditLogEntity(UUID id, String eventType, String actorService,
                          UUID transactionId, String aggregateId, String aggregateType,
                          String previousState, String newState,
                          String eventData, Instant recordedAt) {
        this.id = id;
        this.eventType = eventType;
        this.actorService = actorService;
        this.transactionId = transactionId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.previousState = previousState;
        this.newState = newState;
        this.eventData = eventData;
        this.recordedAt = recordedAt;
    }
}
