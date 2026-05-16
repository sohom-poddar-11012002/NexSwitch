package com.nexswitch.domain.model;

// LEARN: SealedInterface — Completed/Failed; mismatchCount in Completed is the key reconciliation KPI
public sealed interface ReconciliationResult
        permits ReconciliationResult.Completed,
                ReconciliationResult.Failed {

    record Completed(int matchedCount, int mismatchCount, String summaryFileKey) implements ReconciliationResult {}

    record Failed(String reason) implements ReconciliationResult {}
}
