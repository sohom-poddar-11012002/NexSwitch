package com.payments.domain.model.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(
    String eventId,
    String eventType,
    int schemaVersion,
    String aggregateId,
    String aggregateType,
    String producerService,
    Instant occurredAt,
    T payload
) {
    public static <T> DomainEvent<T> of(
            String eventType,
            String aggregateId,
            String aggregateType,
            T payload) {
        return new DomainEvent<>(
            UUID.randomUUID().toString(),
            eventType,
            1,
            aggregateId,
            aggregateType,
            "domain",
            Instant.now(),
            payload
        );
    }
}
