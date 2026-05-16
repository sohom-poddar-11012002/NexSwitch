package com.nexswitch.domain.model.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class DomainEventTest {

    private record SamplePayload(String value) {}

    // ── Factory method ────────────────────────────────────────────────────────

    @Test
    void of_assignsUniqueEventId() {
        var e1 = DomainEvent.of("test.event", "agg-1", "TRANSACTION", "payload");
        var e2 = DomainEvent.of("test.event", "agg-1", "TRANSACTION", "payload");
        assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }

    @Test
    void of_setsSchemaVersionToOne() {
        var event = DomainEvent.of("test.event", "agg-1", "TRANSACTION", "payload");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void of_setsProducerServiceToDomain() {
        var event = DomainEvent.of("test.event", "agg-1", "TRANSACTION", "payload");
        assertThat(event.producerService()).isEqualTo("domain");
    }

    @Test
    void of_setsOccurredAtToNow() {
        var before = Instant.now();
        var event = DomainEvent.of("test.event", "agg-1", "TRANSACTION", "payload");
        var after = Instant.now();
        assertThat(event.occurredAt()).isBetween(before, after);
    }

    @Test
    void of_setsEventType() {
        var event = DomainEvent.of("transaction.authorized", "agg-1", "TRANSACTION", "payload");
        assertThat(event.eventType()).isEqualTo("transaction.authorized");
    }

    @Test
    void of_setsAggregateId() {
        var event = DomainEvent.of("test.event", "txn-uuid-123", "TRANSACTION", "payload");
        assertThat(event.aggregateId()).isEqualTo("txn-uuid-123");
    }

    @Test
    void of_setsAggregateType() {
        var event = DomainEvent.of("test.event", "agg-1", "TRANSACTION", "payload");
        assertThat(event.aggregateType()).isEqualTo("TRANSACTION");
    }

    // ── Payload ───────────────────────────────────────────────────────────────

    @Test
    void carriesTypedPayload() {
        var payload = new SamplePayload("some-data");
        var event = DomainEvent.of("test.event", "agg-1", "TRANSACTION", payload);
        assertThat(event.payload()).isEqualTo(payload);
        assertThat(event.payload()).isInstanceOf(SamplePayload.class);
        assertThat(((SamplePayload) event.payload()).value()).isEqualTo("some-data");
    }

    @Test
    void worksWithNullPayload() {
        var event = DomainEvent.of("test.event", "agg-1", "TRANSACTION", (Void) null);
        assertThat(event.payload()).isNull();
    }

    // ── Record equality ───────────────────────────────────────────────────────

    @Test
    void twoEventsWithSameFieldsAreEqual() {
        var now = Instant.now();
        var e1 = new DomainEvent<>("id", "type", 1, "agg", "TYPE", "domain", now, "p");
        var e2 = new DomainEvent<>("id", "type", 1, "agg", "TYPE", "domain", now, "p");
        assertThat(e1).isEqualTo(e2);
    }
}
