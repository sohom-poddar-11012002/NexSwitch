package com.payments.domain.port.outbound;

import java.time.LocalDate;
import java.util.List;

// LEARN: AdapterPort — S3 key management hidden; domain uses FileCategory enum as logical namespace
public interface FileStoragePort {
    String store(String filename, byte[] content, FileCategory category);
    byte[] retrieve(String fileKey);
    List<String> listByCategory(FileCategory category, LocalDate date);
}
