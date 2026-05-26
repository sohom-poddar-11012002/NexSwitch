package com.nexswitch.domain.port.outbound;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

// LEARN: AdapterPort — S3 key management hidden; domain uses FileCategory enum as logical namespace
public interface FileStoragePort {
    String store(String filename, byte[] content, FileCategory category);

    // LEARN: InputStream overload avoids loading entire file into heap — critical for large settlement
    //        files (multi-GB EOD dumps); the adapter can pipe bytes directly to S3 multipart upload.
    String store(String filename, InputStream content, long contentLength, FileCategory category);

    byte[] retrieve(String fileKey);
    List<String> listByCategory(FileCategory category, LocalDate date);

    // LEARN: Pre-signed URLs let the client upload directly to S3; the service never proxies bytes.
    //        Reduces latency (client → S3 vs client → service → S3) and avoids OOM on large files.
    URI generatePresignedPutUrl(String filename, FileCategory category, Duration ttl);
}
