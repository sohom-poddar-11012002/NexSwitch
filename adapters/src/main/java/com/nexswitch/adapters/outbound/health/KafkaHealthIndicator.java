package com.nexswitch.adapters.outbound.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// LEARN: Spring's default KafkaHealthIndicator (via spring-kafka auto-config) simply sends a
//        metadata request — it can return UP even when brokers are in a degraded state. This
//        custom indicator calls describeCluster() which verifies actual broker reachability and
//        reports the cluster ID, useful for debugging environment mismatches in dev/staging.
@Component("kafkaCustomHealthIndicator")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 3;

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // LEARN: describeCluster() is the lightest AdminClient call — one metadata request
            //        that confirms broker reachability without listing topics or consumer groups.
            String clusterId = client.describeCluster()
                    .clusterId()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("reason", e.getMessage())
                    .build();
        }
    }
}
