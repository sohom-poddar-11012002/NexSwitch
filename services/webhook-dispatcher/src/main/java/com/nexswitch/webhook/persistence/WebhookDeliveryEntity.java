package com.nexswitch.webhook.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// LEARN: @Entity maps to the webhook_deliveries table created in V4 migration.
//        status + created_at are indexed (partial index on PENDING/FAILED) for efficient retry scans.
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WebhookDeliveryEntity() {}

    public WebhookDeliveryEntity(String eventId, String merchantId, String eventType, String payload) {
        this.eventId     = eventId;
        this.merchantId  = merchantId;
        this.eventType   = eventType;
        this.payload     = payload;
        this.status      = WebhookDeliveryStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt   = Instant.now();
    }

    public void recordAttempt(int responseCode) {
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.responseCode  = responseCode;
        if (responseCode >= 200 && responseCode < 300) {
            this.status      = WebhookDeliveryStatus.DELIVERED;
            this.deliveredAt = this.lastAttemptAt;
        }
    }

    public void markFailed() {
        this.status = WebhookDeliveryStatus.FAILED;
        this.lastAttemptAt = Instant.now();
    }

    public UUID id()              { return id; }
    public String eventId()       { return eventId; }
    public String merchantId()    { return merchantId; }
    public String eventType()     { return eventType; }
    public String payload()       { return payload; }
    public WebhookDeliveryStatus status() { return status; }
    public int attemptCount()     { return attemptCount; }
}
