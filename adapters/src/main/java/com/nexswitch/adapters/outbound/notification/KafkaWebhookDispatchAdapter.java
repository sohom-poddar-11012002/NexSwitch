package com.nexswitch.adapters.outbound.notification;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.port.outbound.WebhookDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// LEARN: KafkaWebhookBridge — publishing to a single fan-out topic that webhook-dispatcher
//        consumes decouples acquiring-service from HTTP delivery concerns. The dispatcher
//        handles retries, HMAC signing, and DLQ routing independently.
//        eventType is passed in the payload — NOT used as a Kafka topic name, which would
//        let callers create arbitrary topics by controlling the string.
@Component
public class KafkaWebhookDispatchAdapter implements WebhookDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaWebhookDispatchAdapter.class);
    static final String WEBHOOK_EVENTS_TOPIC = "webhook.events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaWebhookDispatchAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void dispatch(MerchantId merchantId, String eventType, String payloadJson) {
        log.info("webhook.dispatch.kafka topic={} merchantId={} eventType={}",
                WEBHOOK_EVENTS_TOPIC, merchantId.value(), eventType);
        kafkaTemplate.send(WEBHOOK_EVENTS_TOPIC, merchantId.value(), payloadJson);
    }
}
