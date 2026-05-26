package com.nexswitch.adapters.outbound.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// LEARN: EventPublisher — decouples the merchant-admin write path from cache invalidation.
//        Any service that changes merchant config (status, webhook URL, limits) calls publish()
//        instead of directly touching the cache. The Kafka event drives invalidation via
//        MerchantCacheInvalidationListener, which gives eventual consistency within one event round-trip.
@Component
public class MerchantConfigPublisher {

    private static final Logger log = LoggerFactory.getLogger(MerchantConfigPublisher.class);
    private static final String TOPIC = "merchant.config.updated";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public MerchantConfigPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String merchantId) {
        log.info("merchant.config.publishing merchantId={}", merchantId);
        kafkaTemplate.send(TOPIC, merchantId, merchantId);
    }
}
