package com.nexswitch.app.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// LEARN: Kafka producers/consumers are thread-safe — one shared instance per app is the norm.
//        Partitions are the unit of parallelism: consumers in the same group each own a partition,
//        so throughput scales by adding partitions, not consumer threads.
// LEARN: NewTopic beans are idempotent — KafkaAdmin reads them at startup and creates any missing
//        topics. RF=1 + partitions=3 is the minimum viable dev topology; prod uses RF≥3.
@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;

    @Bean public NewTopic transactionAuthorized() { return topic("transaction.authorized"); }
    @Bean public NewTopic transactionDeclined()   { return topic("transaction.declined"); }
    @Bean public NewTopic transactionReversalInitiated() { return topic("transaction.reversal_initiated"); }
    @Bean public NewTopic transactionReversed()   { return topic("transaction.reversed"); }
    @Bean public NewTopic transactionRefunded()   { return topic("transaction.refunded"); }
    @Bean public NewTopic transactionChargebackReceived() { return topic("transaction.chargeback_received"); }
    @Bean public NewTopic merchantConfigUpdated() { return topic("merchant.config.updated"); }
    @Bean public NewTopic webhookEvents()          { return topic("webhook.events"); }
    @Bean public NewTopic webhookDlq()            { return topic("webhook.dlq"); }
    @Bean public NewTopic notificationEvents()    { return topic("notification.events"); }
    @Bean public NewTopic settlementEvents()      { return topic("settlement.events"); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
