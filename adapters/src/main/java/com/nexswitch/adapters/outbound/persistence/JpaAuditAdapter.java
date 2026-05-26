package com.nexswitch.adapters.outbound.persistence;

import com.nexswitch.adapters.outbound.persistence.entity.AuditLogEntity;
import com.nexswitch.adapters.outbound.persistence.jpa.JpaAuditLogRepository;
import com.nexswitch.domain.port.outbound.AuditPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

// LEARN: AppendOnlyAdapter — INSERT only; the DB enforces the no-UPDATE/DELETE invariant,
//        so no Spring @Transactional(readOnly=false) guard is needed here.
@Repository
public class JpaAuditAdapter implements AuditPort {

    private final JpaAuditLogRepository repository;

    public JpaAuditAdapter(JpaAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(String eventType,
                       String actorService,
                       UUID transactionId,
                       String aggregateId,
                       String aggregateType,
                       String previousState,
                       String newState,
                       String eventDataJson) {
        AuditLogEntity entity = new AuditLogEntity(
                UUID.randomUUID(), eventType, actorService,
                transactionId, aggregateId, aggregateType,
                previousState, newState,
                eventDataJson == null ? "{}" : eventDataJson,
                Instant.now()
        );
        repository.save(entity);
    }
}
