package com.nexswitch.domain.port.outbound;

import java.time.LocalDate;
import java.util.UUID;

public interface ReconciliationRunPort {
    UUID startRun(LocalDate date);
    void completeRun(UUID runId, int total, int matched, int mismatch);
}
