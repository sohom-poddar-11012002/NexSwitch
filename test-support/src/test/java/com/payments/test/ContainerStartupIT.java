package com.payments.test;

import com.payments.test.fixture.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies all three singleton containers start successfully and fixtures compile.
 * No Spring context — pure Testcontainers smoke test.
 */
@Tag("integration")
class ContainerStartupIT extends IntegrationTestBase {

    @Test
    void postgres_isRunning() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(POSTGRES.getJdbcUrl()).startsWith("jdbc:postgresql://");
    }

    @Test
    void redis_isRunning() {
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(REDIS.getMappedPort(6379)).isPositive();
    }

    @Test
    void kafka_isRunning() {
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(KAFKA.getBootstrapServers()).contains("localhost:");
    }

    @Test
    void transactionFixture_buildsAllStates() {
        assertThat(TransactionFixture.initiated().status().name()).isEqualTo("INITIATED");
        assertThat(TransactionFixture.authorized().status().name()).isEqualTo("AUTHORIZED");
        assertThat(TransactionFixture.declined().status().name()).isEqualTo("DECLINED");
        assertThat(TransactionFixture.reversalPending().status().name()).isEqualTo("REVERSAL_PENDING");
    }

    @Test
    void merchantFixture_buildsVariants() {
        assertThat(MerchantFixture.active().isActive()).isTrue();
        assertThat(MerchantFixture.suspended().isActive()).isFalse();
    }

    @Test
    void binInfoFixture_buildsVariants() {
        assertThat(BinInfoFixture.visaDebit().nfsEligible()).isFalse();
        assertThat(BinInfoFixture.rupayNfs().nfsEligible()).isTrue();
        assertThat(BinInfoFixture.mastercardCredit().issuerBank()).isNull();
    }

    @Test
    void iso8583Fixture_containsRequiredFields() {
        var fields = Iso8583MessageFixture.valid0100();
        assertThat(fields).containsKey(2);   // PAN
        assertThat(fields).containsKey(4);   // Amount
        assertThat(fields).containsKey(11);  // STAN
        assertThat(fields).containsKey(41);  // Terminal ID
        assertThat(fields.get(41)).isEqualTo("TERM0042");
    }
}
