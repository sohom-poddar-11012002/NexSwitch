package com.payments.domain.model;

public sealed interface ReconciliationResult
        permits ReconciliationResult.Completed,
                ReconciliationResult.Failed {

    record Completed(int matchedCount, int mismatchCount, String summaryFileKey) implements ReconciliationResult {}

    record Failed(String reason) implements ReconciliationResult {}
}
