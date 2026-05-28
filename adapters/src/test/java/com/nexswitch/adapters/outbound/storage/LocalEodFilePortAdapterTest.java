package com.nexswitch.adapters.outbound.storage;

import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.port.outbound.EodFilePort.SettlementRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class LocalEodFilePortAdapterTest {

    @TempDir
    Path tempDir;

    private LocalEodFilePortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalEodFilePortAdapter(tempDir.toString());
    }

    @Test
    void fetchForDate_parsesValidCsvFile() throws IOException {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String txnId = UUID.randomUUID().toString();
        String csv = txnId + ",RRN001,6000.00,INR,VISA,00\n";

        writeEodFile(PaymentNetwork.VISA, date, csv);

        List<SettlementRecord> records = adapter.fetchForDate(date, PaymentNetwork.VISA);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).transactionId().toString()).isEqualTo(txnId);
        assertThat(records.get(0).rrn()).isEqualTo("RRN001");
        assertThat(records.get(0).networkAmount().amount()).isEqualByComparingTo("6000.00");
        assertThat(records.get(0).network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(records.get(0).responseCode()).isEqualTo("00");
    }

    @Test
    void fetchForDate_skipsCommentAndBlankLines() throws IOException {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String txnId = UUID.randomUUID().toString();
        String csv = "# header comment\n\n" + txnId + ",RRN002,5000.00,INR,VISA,00\n";

        writeEodFile(PaymentNetwork.VISA, date, csv);

        List<SettlementRecord> records = adapter.fetchForDate(date, PaymentNetwork.VISA);

        assertThat(records).hasSize(1);
    }

    @Test
    void fetchForDate_returnsEmpty_whenFileNotFound() {
        LocalDate date = LocalDate.of(2026, 5, 1);

        List<SettlementRecord> records = adapter.fetchForDate(date, PaymentNetwork.VISA);

        assertThat(records).isEmpty();
    }

    @Test
    void fetchForDate_skipsInvalidLines() throws IOException {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String txnId = UUID.randomUUID().toString();
        String csv = "BAD LINE NO COMMAS\n" + txnId + ",RRN003,7000.00,INR,MASTERCARD,00\n";

        writeEodFile(PaymentNetwork.MASTERCARD, date, csv);

        List<SettlementRecord> records = adapter.fetchForDate(date, PaymentNetwork.MASTERCARD);

        // Only the valid line is parsed; bad line is skipped with a warning
        assertThat(records).hasSize(1);
        assertThat(records.get(0).network()).isEqualTo(PaymentNetwork.MASTERCARD);
    }

    @Test
    void fetchForDate_parsesMultipleRows() throws IOException {
        LocalDate date = LocalDate.of(2026, 5, 29);
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        String csv = id1 + ",RRN010,1000.00,INR,RUPAY,00\n"
                   + id2 + ",RRN011,2000.00,INR,RUPAY,05\n";

        writeEodFile(PaymentNetwork.RUPAY, date, csv);

        List<SettlementRecord> records = adapter.fetchForDate(date, PaymentNetwork.RUPAY);

        assertThat(records).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeEodFile(PaymentNetwork network, LocalDate date, String content) throws IOException {
        Path networkDir = tempDir.resolve(network.name().toLowerCase());
        Files.createDirectories(networkDir);
        Files.writeString(networkDir.resolve(date + ".csv"), content);
    }
}
