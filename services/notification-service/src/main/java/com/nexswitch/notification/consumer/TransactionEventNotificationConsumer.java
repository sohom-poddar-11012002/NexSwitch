package com.nexswitch.notification.consumer;

import com.nexswitch.domain.port.outbound.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

// LEARN: Notifications-as-consumers — notification-service subscribes to domain events over Kafka
//        so acquiring-service never has a compile-time dependency on email/SMS infrastructure.
@Component
public class TransactionEventNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventNotificationConsumer.class);

    private final NotificationPort notificationPort;

    public TransactionEventNotificationConsumer(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @KafkaListener(
            topics = {"transaction.authorized", "transaction.refunded", "transaction.reversed"},
            groupId = "notification-service"
    )
    public void onTransactionEvent(String payload) {
        log.info("notification.consumer.received payload={}", payload);
        notificationPort.send(
                "merchant@nexswitch.dev",
                "Transaction Event",
                "transaction-event",
                Map.of("payload", payload)
        );
    }
}
