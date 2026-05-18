package com.nexswitch.webhook.consumer;

import com.nexswitch.webhook.service.WebhookDeliveryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// LEARN: ManualAckKafkaConsumer — offset is committed only after delivery succeeds or DLQ is written.
//        Auto-commit would advance the offset on receipt, silently losing events on crash mid-delivery.
//        ENABLE_AUTO_COMMIT=false + AckMode.MANUAL_IMMEDIATE gives at-least-once delivery semantics.
@Component
public class WebhookDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryConsumer.class);

    private final WebhookDeliveryService deliveryService;

    public WebhookDeliveryConsumer(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @KafkaListener(
            topics = {
                "transaction.authorized",
                "transaction.declined",
                "transaction.reversal_initiated",
                "transaction.reversed",
                "transaction.refunded",
                "transaction.chargeback_received"
            },
            groupId = "webhook-dispatcher",
            containerFactory = "webhookKafkaListenerContainerFactory"
    )
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String merchantId = record.key();
        String eventType  = record.topic();
        String payload    = record.value();

        log.info("received event topic={} merchantId={}", eventType, merchantId);
        try {
            deliveryService.dispatch(merchantId, eventType, payload);
            ack.acknowledge();
        } catch (Exception e) {
            // LEARN: NackOnException — do not acknowledge; Kafka will redeliver.
            //        The retry loop inside dispatch() handles transient HTTP failures;
            //        only a bug or OOM would reach here.
            log.error("unexpected error dispatching webhook topic={} merchantId={}", eventType, merchantId, e);
        }
    }
}
