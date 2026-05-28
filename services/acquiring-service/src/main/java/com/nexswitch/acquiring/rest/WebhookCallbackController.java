package com.nexswitch.acquiring.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

// LEARN: HMAC-SHA256 webhook verification — replicates the Stripe / Razorpay pattern.
//        Merchant (or test harness) must sign the body with the shared secret; we reject
//        mismatched signatures with 401 before any business logic runs. This prevents
//        arbitrary parties from faking payment success callbacks.
@RestController
@RequestMapping("/api/webhooks")
public class WebhookCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WebhookCallbackController.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final String webhookSecret;

    public WebhookCallbackController(
            @Value("${webhook.inbound-secret:nexswitch-test-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/inbound")
    public ResponseEntity<Map<String, String>> receiveWebhook(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String body) {

        if (signature == null || !verifySignature(body, signature)) {
            log.warn("webhook.inbound.invalid_signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid signature"));
        }

        log.info("webhook.inbound.received bodyLength={}", body.length());
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    private boolean verifySignature(String payload, String providedSignature) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            String expected = "sha256=" + HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            // LEARN: MessageDigest.isEqual — constant-time comparison prevents timing attacks
            //        that could let an attacker brute-force valid signatures byte by byte.
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    providedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("webhook.inbound.hmac_error", e);
            return false;
        }
    }
}
