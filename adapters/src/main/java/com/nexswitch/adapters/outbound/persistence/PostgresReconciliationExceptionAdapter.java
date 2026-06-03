package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.entity.ReconciliationExceptionEntity;
import com.nexswitch.adapters.outbound.persistence.jpa.ReconciliationExceptionJpaRepository;
import com.nexswitch.domain.model.ReconciliationException;
import com.nexswitch.domain.port.outbound.ReconciliationExceptionPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostgresReconciliationExceptionAdapter implements ReconciliationExceptionPort {

    private final ReconciliationExceptionJpaRepository repository;

    public PostgresReconciliationExceptionAdapter(ReconciliationExceptionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(ReconciliationException exception) {
        repository.save(new ReconciliationExceptionEntity(
                UUID.randomUUID(),
                exception.runId(),
                exception.category().name(),
                exception.severity().name(),
                exception.transactionId(),
                exception.rrn(),
                exception.ourAmount(),
                exception.networkAmount()
        ));
    }
}
