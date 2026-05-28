package com.nexswitch.adapters.outbound.storage;

import com.nexswitch.domain.port.outbound.FileCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class LocalFileStorageAdapterTest {

    // LEARN: @TempDir creates an OS temp directory scoped to the test class; JUnit cleans it up
    //        automatically after the test run. This avoids leaving test artifacts in /tmp.
    @TempDir
    Path tempDir;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalFileStorageAdapter(tempDir.toString());
    }

    @Test
    void store_writesFileAndReturnsPath() {
        byte[] content = "settlement-data".getBytes();

        String key = adapter.store("test.csv", content, FileCategory.SETTLEMENT);

        assertThat(key).endsWith("test.csv");
        assertThat(Path.of(key)).exists();
    }

    @Test
    void retrieve_returnsStoredBytes() {
        byte[] original = "hello-nexswitch".getBytes();
        String key = adapter.store("data.bin", original, FileCategory.SETTLEMENT);

        byte[] retrieved = adapter.retrieve(key);

        assertThat(retrieved).isEqualTo(original);
    }

    @Test
    void store_inputStream_writesFileAndReturnsPath() throws Exception {
        byte[] content = "stream-content".getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(content);

        String key = adapter.store("stream.bin", stream, content.length, FileCategory.SETTLEMENT);

        assertThat(key).endsWith("stream.bin");
        byte[] retrieved = adapter.retrieve(key);
        assertThat(retrieved).isEqualTo(content);
    }

    @Test
    void listByCategory_returnsFilesMatchingDate() {
        LocalDate today = LocalDate.now();
        String filename = today + "-settlement.csv";
        adapter.store(filename, "data".getBytes(), FileCategory.SETTLEMENT);

        List<String> found = adapter.listByCategory(FileCategory.SETTLEMENT, today);

        assertThat(found).hasSize(1);
        assertThat(found.get(0)).endsWith(filename);
    }

    @Test
    void listByCategory_returnsEmpty_whenDirectoryMissing() {
        // No files stored yet for CHARGEBACK category
        List<String> found = adapter.listByCategory(FileCategory.CHARGEBACK_EVIDENCE, LocalDate.now());

        assertThat(found).isEmpty();
    }

    @Test
    void listByCategory_filtersOutNonMatchingDates() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        adapter.store(yesterday + "-old.csv", "old".getBytes(), FileCategory.SETTLEMENT);

        List<String> found = adapter.listByCategory(FileCategory.SETTLEMENT, today);

        assertThat(found).isEmpty();
    }

    @Test
    void generatePresignedPutUrl_returnsFileUri() {
        URI uri = adapter.generatePresignedPutUrl("upload.csv", FileCategory.SETTLEMENT, Duration.ofMinutes(15));

        assertThat(uri.getScheme()).isEqualTo("file");
        assertThat(uri.toString()).contains("upload.csv");
    }

    @Test
    void retrieve_throwsRuntimeException_whenFileNotFound() {
        assertThatThrownBy(() -> adapter.retrieve("/nonexistent/path/file.csv"))
                .isInstanceOf(RuntimeException.class);
    }
}
