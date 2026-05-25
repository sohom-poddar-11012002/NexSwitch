package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.TxnRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ResultTypesTest {

    // RefundResult

    @Test
    void refundResult_initiated_carries_refundId_and_timestamp() {
        var id = UUID.randomUUID();
        var now = Instant.now();
        var result = new RefundResult.Initiated(id, now);
        assertThat(result.refundId()).isEqualTo(id);
        assertThat(result.initiatedAt()).isEqualTo(now);
    }

    @Test
    void refundResult_failed_carries_reason() {
        var result = new RefundResult.Failed("Merchant not eligible");
        assertThat(result.reason()).isEqualTo("Merchant not eligible");
    }

    @Test
    void refundResult_pattern_match_is_exhaustive() {
        RefundResult result = new RefundResult.Initiated(UUID.randomUUID(), Instant.now());
        String label = switch (result) {
            case RefundResult.Initiated i -> "initiated";
            case RefundResult.Failed f    -> "failed";
        };
        assertThat(label).isEqualTo("initiated");
    }

    // ReversalResult

    @Test
    void reversalResult_accepted_carries_timestamp() {
        var now = Instant.now();
        var result = new ReversalResult.Accepted(now);
        assertThat(result.reversedAt()).isEqualTo(now);
    }

    @Test
    void reversalResult_alreadyReversed_constructs() {
        assertThatNoException().isThrownBy(ReversalResult.AlreadyReversed::new);
    }

    @Test
    void reversalResult_failed_carries_reason() {
        var result = new ReversalResult.Failed("Network unavailable");
        assertThat(result.reason()).isEqualTo("Network unavailable");
    }

    @Test
    void reversalResult_pattern_match_is_exhaustive() {
        ReversalResult result = new ReversalResult.Accepted(Instant.now());
        String label = switch (result) {
            case ReversalResult.Accepted a       -> "accepted";
            case ReversalResult.AlreadyReversed r -> "already_reversed";
            case ReversalResult.Failed f         -> "failed";
        };
        assertThat(label).isEqualTo("accepted");
    }

    // QRGenerationResult

    @Test
    void qrGenerationResult_generated_carries_all_fields() {
        var now = Instant.now();
        var result = new QRGenerationResult.Generated(new TxnRef("TXN20260513001"), "base64data==", now);
        assertThat(result.txnRef()).isEqualTo(new TxnRef("TXN20260513001"));
        assertThat(result.qrImageBase64()).isEqualTo("base64data==");
        assertThat(result.expiresAt()).isEqualTo(now);
    }

    @Test
    void qrGenerationResult_failed_carries_reason() {
        var result = new QRGenerationResult.Failed("Merchant VPA not configured");
        assertThat(result.reason()).isEqualTo("Merchant VPA not configured");
    }

    @Test
    void qrGenerationResult_pattern_match_is_exhaustive() {
        QRGenerationResult result = new QRGenerationResult.Failed("error");
        String label = switch (result) {
            case QRGenerationResult.Generated g -> "generated";
            case QRGenerationResult.Failed f    -> "failed";
        };
        assertThat(label).isEqualTo("failed");
    }

    // ReconciliationResult

    @Test
    void reconciliationResult_completed_carries_counts_and_key() {
        var result = new ReconciliationResult.Completed(1620, 3, "RECON_20260512_SUMMARY.json");
        assertThat(result.matchedCount()).isEqualTo(1620);
        assertThat(result.mismatchCount()).isEqualTo(3);
        assertThat(result.summaryFileKey()).isEqualTo("RECON_20260512_SUMMARY.json");
    }

    @Test
    void reconciliationResult_failed_carries_reason() {
        var result = new ReconciliationResult.Failed("Network file missing");
        assertThat(result.reason()).isEqualTo("Network file missing");
    }

    @Test
    void reconciliationResult_pattern_match_is_exhaustive() {
        ReconciliationResult result = new ReconciliationResult.Completed(10, 0, "key");
        String label = switch (result) {
            case ReconciliationResult.Completed c -> "completed";
            case ReconciliationResult.Failed f    -> "failed";
        };
        assertThat(label).isEqualTo("completed");
    }
}
