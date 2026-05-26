package com.nexswitch.adapters.outbound.notification;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.port.outbound.WebhookDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// LEARN: KafkaWebhookBridge — publishing to the same Kafka topic that webhook-dispatcher
//        consumes decouples acquiring-service from HTTP delivery concerns. The dispatcher
//        handles retries, HMAC signing, and DLQ routing independently.
@Component
public class KafkaWebhookDispatchAdapter implements WebhookDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaWebhookDispatchAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaWebhookDispatchAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void dispatch(MerchantId merchantId, String eventType, String payloadJson) {
        log.info("webhook.dispatch.kafka topic={} merchantId={}", eventType, merchantId.value());
        kafkaTemplate.send(eventType, merchantId.value(), payloadJson);
    }
}
