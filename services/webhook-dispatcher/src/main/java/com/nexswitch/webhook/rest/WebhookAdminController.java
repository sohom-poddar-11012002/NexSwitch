package com.nexswitch.webhook.rest;

import com.nexswitch.webhook.persistence.JpaWebhookDeliveryRepository;
import com.nexswitch.webhook.persistence.WebhookDeliveryEntity;
import com.nexswitch.webhook.persistence.WebhookDeliveryStatus;
import com.nexswitch.webhook.service.HmacSigner;
import com.nexswitch.webhook.service.WebhookDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

// LEARN: DLQ replay — re-queues a FAILED delivery by calling dispatch() again, which creates
//        a fresh delivery attempt independent of the original failed record.
@Tag(name = "Webhook Admin", description = "Internal operations: DLQ replay and HMAC self-test")
@RestController
@RequestMapping("/admin/webhook")
public class WebhookAdminController {

    private static final Logger log = LoggerFactory.getLogger(WebhookAdminController.class);

    private final JpaWebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryService       deliveryService;

    public WebhookAdminController(JpaWebhookDeliveryRepository deliveryRepository,
                                  WebhookDeliveryService deliveryService) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryService    = deliveryService;
    }

    @Operation(summary = "Replay failed webhook delivery",
               description = "Re-triggers delivery for a dead-letter event by eventId; only FAILED deliveries are eligible")
    @PostMapping("/replay/{eventId}")
    public ResponseEntity<Map<String, String>> replay(@PathVariable String eventId) {
        Optional<WebhookDeliveryEntity> maybeEntity = deliveryRepository.findByEventId(eventId);
        if (maybeEntity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        WebhookDeliveryEntity entity = maybeEntity.get();
        if (entity.status() != WebhookDeliveryStatus.FAILED) {
            return ResponseEntity.badRequest()
                    .body(Map.of("reason", "Only FAILED deliveries can be replayed; current status: " + entity.status()));
        }
        log.info("webhook.replay.triggered eventId={} merchantId={}", eventId, entity.merchantId());
        deliveryService.dispatch(entity.merchantId(), entity.eventType(), entity.payload());
        return ResponseEntity.accepted().body(Map.of("status", "REPLAYING", "eventId", eventId));
    }

    @Operation(summary = "HMAC self-test",
               description = "Allows a merchant to verify their webhook secret produces the expected X-NexSwitch-Signature for a given payload")
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestBody VerifyRequest req) {
        if (req.secret() == null || req.secret().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("reason", "secret is required"));
        }
        if (req.payload() == null) {
            return ResponseEntity.badRequest().body(Map.of("reason", "payload is required"));
        }
        String computed = HmacSigner.sign(req.secret(), req.payload());
        boolean matches = req.expectedSignature() != null && req.expectedSignature().equals(computed);
        return ResponseEntity.ok(Map.of(
                "computedSignature", computed,
                "matches", String.valueOf(matches)
        ));
    }

    public record VerifyRequest(String secret, String payload, String expectedSignature) {}
}
