package com.nexswitch.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ReconciliationRunTest {

    @Test
    void createsReconciliationRun() {
        ReconciliationRun run = new ReconciliationRun(
            UUID.randomUUID(),
            LocalDate.of(2026, 5, 11),
            ReconciliationRun.Status.RUNNING,
            Instant.now()
        );

        assertThat(run.runDate()).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(run.status()).isEqualTo(ReconciliationRun.Status.RUNNING);
        assertThat(run.totalTransactions()).isZero();
        assertThat(run.matchedCount()).isZero();
        assertThat(run.mismatchCount()).isZero();
    }

    @Test
    void withResultsReturnsNewInstanceWithCounts() {
        ReconciliationRun run = new ReconciliationRun(
            UUID.randomUUID(), LocalDate.now(),
            ReconciliationRun.Status.RUNNING, Instant.now()
        );

        ReconciliationRun completed = run.withResults(1847, 1844, 3, 0);
        assertThat(completed.totalTransactions()).isEqualTo(1847);
        assertThat(completed.matchedCount()).isEqualTo(1844);
        assertThat(completed.mismatchCount()).isEqualTo(3);
        assertThat(run.totalTransactions()).isZero();
    }

    @Test
    void statusEnumHasExpectedValues() {
        assertThat(ReconciliationRun.Status.values()).containsExactlyInAnyOrder(
            ReconciliationRun.Status.RUNNING,
            ReconciliationRun.Status.COMPLETED,
            ReconciliationRun.Status.FAILED,
            ReconciliationRun.Status.PARTIAL
        );
    }

    @Test
    void throwsWhenRunDateIsNull() {
        assertThatThrownBy(() -> new ReconciliationRun(
            UUID.randomUUID(), null,
            ReconciliationRun.Status.RUNNING, Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
