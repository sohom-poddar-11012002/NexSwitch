package com.nexswitch.webhook.service;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import com.nexswitch.webhook.persistence.JpaWebhookDeliveryRepository;
import com.nexswitch.webhook.persistence.WebhookDeliveryEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// LEARN: ExponentialBackoff — doubling the delay between retries (1s→2s→4s→8s→16s) avoids
//        hammering a struggling endpoint. Jitter (randomising the delay slightly) is the
//        production refinement; the base doubling is the critical part.
//
// LEARN: DLQ (Dead Letter Queue) — after max retries the event is not silently dropped;
//        it is published to a separate Kafka topic so ops can inspect, replay, or escalate.
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final long BASE_BACKOFF_MS = 1_000;

    private final MerchantRepository           merchantRepository;
    private final JpaWebhookDeliveryRepository deliveryRepository;
    private final HttpSender                   httpSender;
    private final int                          maxAttempts;
    private final DlqPublisher                 dlqPublisher;

    public WebhookDeliveryService(
            MerchantRepository merchantRepository,
            JpaWebhookDeliveryRepository deliveryRepository,
            HttpSender httpSender,
            @Value("${webhook.max-attempts:5}") int maxAttempts) {
        this(merchantRepository, deliveryRepository, httpSender, maxAttempts, delivery -> {});
    }

    public WebhookDeliveryService(
            MerchantRepository merchantRepository,
            JpaWebhookDeliveryRepository deliveryRepository,
            HttpSender httpSender,
            int maxAttempts,
            DlqPublisher dlqPublisher) {
        this.merchantRepository = merchantRepository;
        this.deliveryRepository = deliveryRepository;
        this.httpSender         = httpSender;
        this.maxAttempts        = maxAttempts;
        this.dlqPublisher       = dlqPublisher;
    }

    public void dispatch(String merchantId, String eventType, String payloadJson) {
        Optional<MerchantProfile> profile = merchantRepository.findById(MerchantId.of(merchantId));
        if (profile.isEmpty()) {
            log.debug("merchant not found merchantId={} — skipping webhook", merchantId);
            return;
        }

        MerchantProfile merchant = profile.get();
        if (merchant.webhookUrl() == null || merchant.webhookUrl().isBlank()) {
            log.debug("no webhook configured merchantId={} — skipping", merchantId);
            return;
        }

        String eventId = UUID.randomUUID().toString();
        WebhookDeliveryEntity delivery = deliveryRepository.save(
                new WebhookDeliveryEntity(eventId, merchantId, eventType, payloadJson));

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Map<String, String> headers = Map.of(
                    "Content-Type",          "application/json",
                    "X-Event-Type",          eventType,
                    "X-Idempotency-Key",     eventId,
                    "X-NexSwitch-Signature", HmacSigner.sign(merchant.webhookSecret(), payloadJson)
            );

            int status = httpSender.post(merchant.webhookUrl(), payloadJson, headers);
            delivery.recordAttempt(status);
            deliveryRepository.save(delivery);

            if (status >= 200 && status < 300) {
                log.info("webhook delivered merchantId={} eventType={} attempt={}", merchantId, eventType, attempt);
                return;
            }

            log.warn("webhook attempt failed merchantId={} eventType={} attempt={} status={}",
                    merchantId, eventType, attempt, status);

            if (attempt < maxAttempts) {
                // LEARN: ExponentialBackoff — delay doubles each attempt; Thread.sleep is acceptable
                //        here because @KafkaListener threads are I/O-bound waiting on HTTP anyway.
                backoff(attempt);
            }
        }

        delivery.markFailed();
        deliveryRepository.save(delivery);
        dlqPublisher.publish(delivery);
        log.error("webhook exhausted retries — sent to DLQ merchantId={} eventType={}", merchantId, eventType);
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Functional interfaces for testability ─────────────────────────────────

    @FunctionalInterface
    public interface HttpSender {
        int post(String url, String body, Map<String, String> headers);
    }

    @FunctionalInterface
    public interface DlqPublisher {
        void publish(WebhookDeliveryEntity delivery);
    }
}
