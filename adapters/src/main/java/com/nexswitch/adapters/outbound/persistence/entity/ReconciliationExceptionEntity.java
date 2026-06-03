package com.nexswitch.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_exceptions")
public class ReconciliationExceptionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "run_id", nullable = false, columnDefinition = "UUID")
    private UUID runId;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @Column(name = "transaction_id", columnDefinition = "UUID")
    private UUID transactionId;

    @Column(name = "arn", length = 23)
    private String arn;

    @Column(name = "our_amount", precision = 15, scale = 2)
    private BigDecimal ourAmount;

    @Column(name = "network_amount", precision = 15, scale = 2)
    private BigDecimal networkAmount;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconciliationExceptionEntity() {}

    public ReconciliationExceptionEntity(UUID id, UUID runId, String category, String severity,
                                          UUID transactionId, String arn,
                                          BigDecimal ourAmount, BigDecimal networkAmount) {
        this.id = id;
        this.runId = runId;
        this.category = category;
        this.severity = severity;
        this.transactionId = transactionId;
        this.arn = arn;
        this.ourAmount = ourAmount;
        this.networkAmount = networkAmount;
        this.resolved = false;
        this.createdAt = Instant.now();
    }
}
