package com.payments.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReconciliationRun {

    private final UUID id;
    private final LocalDate runDate;
    private final Status status;
    private final int totalTransactions;
    private final int matchedCount;
    private final int mismatchCount;
    private final int unknownResolvedCount;
    private final Instant startedAt;
    private final Instant completedAt;

    public ReconciliationRun(UUID id, LocalDate runDate, Status status, Instant startedAt) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (runDate == null) throw new IllegalArgumentException("runDate must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (startedAt == null) throw new IllegalArgumentException("startedAt must not be null");

        this.id = id;
        this.runDate = runDate;
        this.status = status;
        this.startedAt = startedAt;
        this.totalTransactions = 0;
        this.matchedCount = 0;
        this.mismatchCount = 0;
        this.unknownResolvedCount = 0;
        this.completedAt = null;
    }

    private ReconciliationRun(UUID id, LocalDate runDate, Status status,
                               int totalTransactions, int matchedCount,
                               int mismatchCount, int unknownResolvedCount,
                               Instant startedAt, Instant completedAt) {
        this.id = id;
        this.runDate = runDate;
        this.status = status;
        this.totalTransactions = totalTransactions;
        this.matchedCount = matchedCount;
        this.mismatchCount = mismatchCount;
        this.unknownResolvedCount = unknownResolvedCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public UUID id() { return id; }
    public LocalDate runDate() { return runDate; }
    public Status status() { return status; }
    public int totalTransactions() { return totalTransactions; }
    public int matchedCount() { return matchedCount; }
    public int mismatchCount() { return mismatchCount; }
    public int unknownResolvedCount() { return unknownResolvedCount; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }

    public ReconciliationRun withResults(int total, int matched, int mismatched, int unknownResolved) {
        return new ReconciliationRun(id, runDate, Status.COMPLETED,
            total, matched, mismatched, unknownResolved,
            startedAt, Instant.now());
    }

    public enum Status { RUNNING, COMPLETED, FAILED, PARTIAL }
}
