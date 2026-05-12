package com.payments.domain.port.outbound;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class OutboundPortTypesTest {

    // FileCategory

    @Test
    void fileCategory_values_are_all_accessible() {
        assertThat(FileCategory.values()).containsExactlyInAnyOrder(
                FileCategory.SETTLEMENT,
                FileCategory.RECONCILIATION_REPORT,
                FileCategory.CHARGEBACK_EVIDENCE,
                FileCategory.PAYOUT_REPORT,
                FileCategory.BANK_STATEMENT,
                FileCategory.TEMP
        );
    }

    @Test
    void fileCategory_valueOf_returns_correct_constant() {
        assertThat(FileCategory.valueOf("SETTLEMENT")).isEqualTo(FileCategory.SETTLEMENT);
    }

    // SettlementResult

    @Test
    void settlementResult_submitted_carries_batchId_and_timestamp() {
        var now = Instant.now();
        var result = new SettlementResult.Submitted("VISA-BATCH-20260512-001", now);
        assertThat(result.networkBatchId()).isEqualTo("VISA-BATCH-20260512-001");
        assertThat(result.submittedAt()).isEqualTo(now);
    }

    @Test
    void settlementResult_failed_carries_reason() {
        var result = new SettlementResult.Failed("Network endpoint unreachable");
        assertThat(result.reason()).isEqualTo("Network endpoint unreachable");
    }

    @Test
    void settlementResult_pattern_match_is_exhaustive() {
        SettlementResult result = new SettlementResult.Submitted("BATCH-001", Instant.now());
        String label = switch (result) {
            case SettlementResult.Submitted s -> "submitted";
            case SettlementResult.Failed f    -> "failed";
        };
        assertThat(label).isEqualTo("submitted");
    }
}
