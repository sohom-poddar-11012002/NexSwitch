package com.nexswitch.adapters.outbound.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexswitch.domain.model.event.DomainEvent;
import com.nexswitch.domain.port.outbound.DomainEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// LEARN: TransactionalOutbox-lite — events are pulled after DB save and published to Kafka.
//        If Kafka is down, the event is logged at WARN level. For strict at-least-once
//        delivery, the outbox_events table (V9 migration) can be used as a durable queue.
@Component
public class KafkaDomainEventPublisher implements DomainEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaDomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                      ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        try {
            String payload = objectMapper.writeValueAsString(event.payload());
            kafkaTemplate.send(event.eventType(), event.aggregateId(), payload);
            log.debug("domain.event.published topic={} aggregateId={}",
                    event.eventType(), event.aggregateId());
        } catch (JsonProcessingException e) {
            log.warn("domain.event.serialize_fail topic={} aggregateId={}",
                    event.eventType(), event.aggregateId(), e);
        } catch (Exception e) {
            log.warn("domain.event.publish_fail topic={} aggregateId={}",
                    event.eventType(), event.aggregateId(), e);
        }
    }
}
