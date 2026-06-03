package com.nexswitch.adapters.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRunEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "run_date", nullable = false, unique = true)
    private LocalDate runDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_transactions")
    private Integer totalTransactions;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "mismatch_count")
    private Integer mismatchCount;

    @Column(name = "unknown_resolved_count")
    private Integer unknownResolvedCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReconciliationRunEntity() {}

    public ReconciliationRunEntity(UUID id, LocalDate runDate, String status, Instant startedAt) {
        this.id = id;
        this.runDate = runDate;
        this.status = status;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public String getStatus() { return status; }

    public void complete(int total, int matched, int mismatch) {
        this.totalTransactions = total;
        this.matchedCount = matched;
        this.mismatchCount = mismatch;
        this.unknownResolvedCount = 0;
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
    }
}
