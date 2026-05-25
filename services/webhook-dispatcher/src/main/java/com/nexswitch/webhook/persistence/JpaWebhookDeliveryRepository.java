package com.nexswitch.webhook.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaWebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {
    Optional<WebhookDeliveryEntity> findByEventId(String eventId);
}
