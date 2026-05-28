package com.nexswitch.adapters.outbound.storage;

import com.nexswitch.domain.port.outbound.FileCategory;
import com.nexswitch.domain.port.outbound.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

// LEARN: local profile uses filesystem; production uses S3 via LocalStack — same port, swapped adapter.
//        @Profile("local") ensures this bean is absent in production; the S3 adapter takes its place.
//        Constructor injection of @Value is preferred over field injection for testability.
@Component
@Profile("local")
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    private final String baseDir;

    public LocalFileStorageAdapter(
            @Value("${storage.local.base-dir:/tmp/nexswitch}") String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public String store(String filename, byte[] content, FileCategory category) {
        try {
            Path dir = categoryDir(category);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.write(target, content);
            log.info("storage.local.store category={} filename={} bytes={}", category, filename, content.length);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + filename, e);
        }
    }

    @Override
    public String store(String filename, InputStream content, long contentLength, FileCategory category) {
        try {
            Path dir = categoryDir(category);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(content, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("storage.local.store.stream category={} filename={}", category, filename);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store streamed file: " + filename, e);
        }
    }

    @Override
    public byte[] retrieve(String fileKey) {
        try {
            return Files.readAllBytes(Path.of(fileKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve file: " + fileKey, e);
        }
    }

    @Override
    public List<String> listByCategory(FileCategory category, LocalDate date) {
        Path dir = categoryDir(category);
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> paths = Files.list(dir)) {
            String dateStr = date.toString();
            return paths
                    .filter(p -> p.getFileName().toString().contains(dateStr))
                    .map(p -> p.toString())
                    .toList();
        } catch (IOException e) {
            log.warn("storage.local.list.failed category={} date={}", category, date);
            return List.of();
        }
    }

    @Override
    public URI generatePresignedPutUrl(String filename, FileCategory category, Duration ttl) {
        // LEARN: Pre-signed URLs are an S3 concept; local filesystem has no equivalent.
        //        Return a file:// URI as a dev-only placeholder. The adapter contract is fulfilled.
        Path target = categoryDir(category).resolve(filename);
        log.warn("storage.local.presigned_url — returning file URI (S3 only in production). path={}", target);
        return target.toUri();
    }

    private Path categoryDir(FileCategory category) {
        return Path.of(baseDir, category.name().toLowerCase());
    }
}
