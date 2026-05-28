package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.event.DomainEvent;

// LEARN: DomainEventPublisher — adapter calls pullDomainEvents() after persisting and routes
//        each event to its Kafka topic by eventType. Domain never imports Kafka.
public interface DomainEventPublisherPort {
    void publish(DomainEvent<?> event);
}
