package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.entity.ReconciliationRunEntity;
import com.nexswitch.adapters.outbound.persistence.jpa.ReconciliationRunJpaRepository;
import com.nexswitch.domain.port.outbound.ReconciliationRunPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class PostgresReconciliationRunAdapter implements ReconciliationRunPort {

    private final ReconciliationRunJpaRepository repository;

    public PostgresReconciliationRunAdapter(ReconciliationRunJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public UUID startRun(LocalDate date) {
        UUID id = UUID.randomUUID();
        repository.save(new ReconciliationRunEntity(id, date, "RUNNING", Instant.now()));
        return id;
    }

    @Override
    public void completeRun(UUID runId, int total, int matched, int mismatch) {
        repository.findById(runId).ifPresent(entity -> {
            entity.complete(total, matched, mismatch);
            repository.save(entity);
        });
    }
}
