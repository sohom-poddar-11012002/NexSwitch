package com.nexswitch.domain.model.event;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// LEARN: DomainEvent — generic envelope with schemaVersion for Avro evolution compatibility
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
        return of(Clock.systemUTC(), eventType, aggregateId, aggregateType, payload);
    }

    public static <T> DomainEvent<T> of(
            Clock clock,
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
            Instant.now(clock),
            payload
        );
    }
}
