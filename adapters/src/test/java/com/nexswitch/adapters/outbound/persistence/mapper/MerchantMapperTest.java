package com.nexswitch.adapters.outbound.persistence.mapper;

import com.nexswitch.adapters.outbound.persistence.entity.MerchantEntity;
import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantMapperTest {

    private static final Currency INR = Currency.getInstance("INR");

    private MerchantMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MerchantMapperImpl();
    }

    // ── toEntity ─────────────────────────────────────────────────────────────

    @Test
    void toEntity_mapsAllFields() {
        MerchantProfile domain = activeMerchant();

        MerchantEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo("MERCH0000001");
        assertThat(entity.getName()).isEqualTo("Test Merchant");
        assertThat(entity.getMcc()).isEqualTo("5411");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getMdrPercentage()).isEqualByComparingTo(new BigDecimal("0.0150"));
        assertThat(entity.getPerTxnLimit()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(entity.getDailyLimit()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        assertThat(entity.getReservePercentage()).isEqualByComparingTo(new BigDecimal("0.0500"));
    }

    @Test
    void toEntity_mapsWebhookFields_whenPresent() {
        MerchantProfile domain = activeMerchant();

        MerchantEntity entity = mapper.toEntity(domain);

        assertThat(entity.getWebhookUrl()).isEqualTo("https://merchant.example.com/webhooks");
        assertThat(entity.getWebhookSecret()).isEqualTo("webhook-secret-key");
    }

    @Test
    void toEntity_webhookFields_nullWhenAbsent() {
        MerchantProfile domain = new MerchantProfile(
            new MerchantId("MERCH0000002"),
            "No Webhook Merchant",
            "5411",
            MerchantProfile.Status.ACTIVE,
            Money.of(new BigDecimal("500000.00"), INR),
            Money.of(new BigDecimal("5000000.00"), INR),
            new BigDecimal("0.0150"),
            new BigDecimal("0.0500"),
            null,
            null
        );

        MerchantEntity entity = mapper.toEntity(domain);

        assertThat(entity.getWebhookUrl()).isNull();
        assertThat(entity.getWebhookSecret()).isNull();
    }

    @Test
    void toEntity_ignoredFields_remainDefault() {
        MerchantProfile domain = activeMerchant();

        MerchantEntity entity = mapper.toEntity(domain);

        assertThat(entity.getVpa()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    void toEntity_suspendedStatus_mapsCorrectly() {
        MerchantProfile domain = new MerchantProfile(
            new MerchantId("MERCH0000003"),
            "Suspended Merchant",
            "5411",
            MerchantProfile.Status.SUSPENDED,
            Money.of(new BigDecimal("500000.00"), INR),
            Money.of(new BigDecimal("5000000.00"), INR),
            new BigDecimal("0.0150"),
            new BigDecimal("0.0500"),
            null,
            null
        );

        MerchantEntity entity = mapper.toEntity(domain);

        assertThat(entity.getStatus()).isEqualTo("SUSPENDED");
    }

    // ── toDomain ─────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllFields() {
        MerchantEntity entity = activeMerchantEntity();

        MerchantProfile domain = mapper.toDomain(entity);

        assertThat(domain.merchantId()).isEqualTo(new MerchantId("MERCH0000001"));
        assertThat(domain.name()).isEqualTo("Test Merchant");
        assertThat(domain.mcc()).isEqualTo("5411");
        assertThat(domain.status()).isEqualTo(MerchantProfile.Status.ACTIVE);
        assertThat(domain.mdrPercentage()).isEqualByComparingTo(new BigDecimal("0.0150"));
        assertThat(domain.reservePercentage()).isEqualByComparingTo(new BigDecimal("0.0500"));
    }

    @Test
    void toDomain_limitsAlwaysInr() {
        MerchantEntity entity = activeMerchantEntity();

        MerchantProfile domain = mapper.toDomain(entity);

        assertThat(domain.perTransactionLimit().currency()).isEqualTo(INR);
        assertThat(domain.dailyLimit().currency()).isEqualTo(INR);
        assertThat(domain.perTransactionLimit().amount()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(domain.dailyLimit().amount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
    }

    @Test
    void toDomain_mapsWebhookFields() {
        MerchantEntity entity = activeMerchantEntity();

        MerchantProfile domain = mapper.toDomain(entity);

        assertThat(domain.webhookUrl()).isEqualTo("https://merchant.example.com/webhooks");
        assertThat(domain.webhookSecret()).isEqualTo("webhook-secret-key");
    }

    @Test
    void toDomain_mapsTerminatedStatus() {
        MerchantEntity entity = activeMerchantEntity();
        entity.setStatus("TERMINATED");

        MerchantProfile domain = mapper.toDomain(entity);

        assertThat(domain.status()).isEqualTo(MerchantProfile.Status.TERMINATED);
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void roundTrip_entityToDomainToEntity_preservesAllMappedFields() {
        MerchantEntity original = activeMerchantEntity();

        MerchantProfile domain = mapper.toDomain(original);
        MerchantEntity restored = mapper.toEntity(domain);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getMcc()).isEqualTo(original.getMcc());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getMdrPercentage()).isEqualByComparingTo(original.getMdrPercentage());
        assertThat(restored.getPerTxnLimit()).isEqualByComparingTo(original.getPerTxnLimit());
        assertThat(restored.getDailyLimit()).isEqualByComparingTo(original.getDailyLimit());
        assertThat(restored.getReservePercentage()).isEqualByComparingTo(original.getReservePercentage());
        assertThat(restored.getWebhookUrl()).isEqualTo(original.getWebhookUrl());
        assertThat(restored.getWebhookSecret()).isEqualTo(original.getWebhookSecret());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MerchantProfile activeMerchant() {
        return new MerchantProfile(
            new MerchantId("MERCH0000001"),
            "Test Merchant",
            "5411",
            MerchantProfile.Status.ACTIVE,
            Money.of(new BigDecimal("500000.00"), INR),
            Money.of(new BigDecimal("5000000.00"), INR),
            new BigDecimal("0.0150"),
            new BigDecimal("0.0500"),
            "https://merchant.example.com/webhooks",
            "webhook-secret-key"
        );
    }

    private MerchantEntity activeMerchantEntity() {
        MerchantEntity e = new MerchantEntity();
        e.setId("MERCH0000001");
        e.setName("Test Merchant");
        e.setMcc("5411");
        e.setStatus("ACTIVE");
        e.setMdrPercentage(new BigDecimal("0.0150"));
        e.setPerTxnLimit(new BigDecimal("500000.00"));
        e.setDailyLimit(new BigDecimal("5000000.00"));
        e.setReservePercentage(new BigDecimal("0.0500"));
        e.setWebhookUrl("https://merchant.example.com/webhooks");
        e.setWebhookSecret("webhook-secret-key");
        e.setCreatedAt(Instant.now());
        return e;
    }
}
