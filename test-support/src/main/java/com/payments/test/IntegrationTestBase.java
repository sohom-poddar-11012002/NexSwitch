package com.payments.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base for all integration tests. Starts Postgres, Redis, and Kafka once per JVM
 * and reuses them across every subclass — no per-class container spin-up overhead.
 *
 * Reuse requires ~/.testcontainers.properties:
 *   testcontainers.reuse.enable=true
 *
 * Subclasses add their own @SpringBootTest (or slice annotation) — this class intentionally
 * carries no Spring context so it can be reused across all service modules.
 *
 * Usage:
 *   @SpringBootTest
 *   class MyRepositoryIT extends IntegrationTestBase { ... }
 */
// LEARN: TestcontainersSingleton — static containers shared across all test classes; withReuse(true) survives JVM restarts
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("payments_test")
            .withUsername("payments_app")
            .withPassword("test_password")
            .withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:8-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "test_password")
            .withReuse(true);

    // LEARN: TC 2.x KafkaContainer defaults to apache/kafka (official ASF image, KRaft-only).
    //        confluentinc/cp-kafka has different startup log patterns so its wait strategy fails.
    @Container
    static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka:latest"))
            .withReuse(true);

    @DynamicPropertySource
    static void configureContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",           POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",      POSTGRES::getUsername);
        registry.add("spring.datasource.password",      POSTGRES::getPassword);
        registry.add("spring.flyway.url",               POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",              POSTGRES::getUsername);
        registry.add("spring.flyway.password",          POSTGRES::getPassword);
        registry.add("spring.data.redis.host",          REDIS::getHost);
        registry.add("spring.data.redis.port",          () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password",      () -> "test_password");
        registry.add("spring.kafka.bootstrap-servers",  KAFKA::getBootstrapServers);
    }
}
