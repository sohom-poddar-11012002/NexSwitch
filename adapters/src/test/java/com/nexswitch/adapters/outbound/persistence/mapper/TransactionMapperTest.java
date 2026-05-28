package com.nexswitch.adapters.outbound.persistence.mapper;

import com.nexswitch.adapters.outbound.persistence.entity.TransactionEntity;
import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.model.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    private static final Currency INR = Currency.getInstance("INR");

    private TransactionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TransactionMapperImpl();
    }

    // ── toEntity ─────────────────────────────────────────────────────────────

    @Test
    void toEntity_mapsScalarFields() {
        Transaction domain = fullTransaction();

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo(domain.id());
        assertThat(entity.getMerchantId()).isEqualTo("MERCH0000001");
        assertThat(entity.getTerminalId()).isEqualTo("TERM0001");
        assertThat(entity.getPanHash()).isEqualTo("a".repeat(64));
        assertThat(entity.getStan()).isEqualTo("123456");
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(entity.getCurrency()).isEqualTo("INR");
        assertThat(entity.getNetwork()).isEqualTo("VISA");
        assertThat(entity.getPaymentMethod()).isEqualTo("CARD_CHIP");
        assertThat(entity.getStatus()).isEqualTo("INITIATED");
        assertThat(entity.getResponseCode()).isNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void toEntity_mapsAuthorizationCode_whenPresent() {
        Transaction domain = fullTransaction()
            .withStatus(TransactionStatus.AUTHORIZED)
            .withAuthCode(AuthorizationCode.of("AB1234"));

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getAuthorizationCode()).isEqualTo("AB1234");
    }

    @Test
    void toEntity_authorizationCode_nullWhenAbsent() {
        Transaction domain = fullTransaction();

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getAuthorizationCode()).isNull();
    }

    @Test
    void toEntity_mapsArn_whenPresent() {
        Transaction domain = fullTransaction()
            .withArn(AcquirerReferenceNumber.of("A".repeat(23)));

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getArn()).isEqualTo("A".repeat(23));
    }

    @Test
    void toEntity_arn_nullWhenAbsent() {
        Transaction domain = fullTransaction();

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getArn()).isNull();
    }

    @Test
    void toEntity_ignoredFields_remainDefault() {
        Transaction domain = fullTransaction();

        TransactionEntity entity = mapper.toEntity(domain);

        assertThat(entity.getRrn()).isNull();
        // cardLast4 is populated from Transaction.cardLast4() if present (null in fixture = null here)
        assertThat(entity.getCardLast4()).isNull();
        assertThat(entity.getRiskScore()).isNull();
        // idempotencyKey is always derived from terminalId:stan — never null after N47 fix
        assertThat(entity.getIdempotencyKey()).isNotNull();
        assertThat(entity.getNpciTxnId()).isNull();
        assertThat(entity.getQrTxnRef()).isNull();
        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getUpstreamRequestAt()).isNull();
        assertThat(entity.getUpstreamResponseAt()).isNull();
        assertThat(entity.getSettledAt()).isNull();
        assertThat(entity.getReconciledAt()).isNull();
        assertThat(entity.getPaidOutAt()).isNull();
    }

    // ── toDomain ─────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllFields() {
        TransactionEntity entity = fullEntity();

        Transaction domain = mapper.toDomain(entity);

        assertThat(domain.id()).isEqualTo(entity.getId());
        assertThat(domain.merchantId()).isEqualTo(new MerchantId("MERCH0000001"));
        assertThat(domain.terminalId()).isEqualTo(new TerminalId("TERM0001"));
        assertThat(domain.panHash()).isEqualTo(PanHash.of("a".repeat(64)));
        assertThat(domain.stan()).isEqualTo(SystemTraceAuditNumber.of("123456"));
        assertThat(domain.amount()).isEqualTo(Money.of(new BigDecimal("6000.00"), INR));
        assertThat(domain.network()).isEqualTo(PaymentNetwork.VISA);
        assertThat(domain.paymentMethod()).isEqualTo(PaymentMethod.CARD_CHIP);
        assertThat(domain.status()).isEqualTo(TransactionStatus.INITIATED);
        assertThat(domain.authorizationCode()).isNull();
        assertThat(domain.arn()).isNull();
        assertThat(domain.responseCode()).isNull();
        assertThat(domain.createdAt()).isNotNull();
        assertThat(domain.updatedAt()).isNotNull();
    }

    @Test
    void toDomain_mapsAuthorizationCode_whenPresent() {
        TransactionEntity entity = fullEntity();
        entity.setAuthorizationCode("AB1234");
        entity.setStatus("AUTHORIZED");

        Transaction domain = mapper.toDomain(entity);

        assertThat(domain.authorizationCode()).isEqualTo(AuthorizationCode.of("AB1234"));
    }

    @Test
    void toDomain_mapsArn_whenPresent() {
        TransactionEntity entity = fullEntity();
        entity.setArn("A".repeat(23));

        Transaction domain = mapper.toDomain(entity);

        assertThat(domain.arn()).isEqualTo(AcquirerReferenceNumber.of("A".repeat(23)));
    }

    @Test
    void toDomain_mapsResponseCode_whenPresent() {
        TransactionEntity entity = fullEntity();
        entity.setResponseCode("51");
        entity.setStatus("DECLINED");

        Transaction domain = mapper.toDomain(entity);

        assertThat(domain.responseCode()).isEqualTo("51");
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void roundTrip_entityToDomainToEntity_preservesAllMappedFields() {
        TransactionEntity original = fullEntity();

        Transaction domain = mapper.toDomain(original);
        TransactionEntity restored = mapper.toEntity(domain);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getMerchantId()).isEqualTo(original.getMerchantId());
        assertThat(restored.getTerminalId()).isEqualTo(original.getTerminalId());
        assertThat(restored.getPanHash()).isEqualTo(original.getPanHash());
        assertThat(restored.getStan()).isEqualTo(original.getStan());
        assertThat(restored.getAmount()).isEqualByComparingTo(original.getAmount());
        assertThat(restored.getCurrency()).isEqualTo(original.getCurrency());
        assertThat(restored.getNetwork()).isEqualTo(original.getNetwork());
        assertThat(restored.getPaymentMethod()).isEqualTo(original.getPaymentMethod());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Test
    void roundTrip_withAuthCodeAndArn_preserved() {
        TransactionEntity original = fullEntity();
        original.setAuthorizationCode("XY9876");
        original.setArn("B".repeat(23));
        original.setStatus("AUTHORIZED");

        Transaction domain = mapper.toDomain(original);
        TransactionEntity restored = mapper.toEntity(domain);

        assertThat(restored.getAuthorizationCode()).isEqualTo("XY9876");
        assertThat(restored.getArn()).isEqualTo("B".repeat(23));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction fullTransaction() {
        Instant now = Instant.now();
        return Transaction.builder()
            .id(UUID.randomUUID())
            .merchantId(new MerchantId("MERCH0000001"))
            .terminalId(new TerminalId("TERM0001"))
            .amount(Money.of(new BigDecimal("6000.00"), INR))
            .network(PaymentNetwork.VISA)
            .paymentMethod(PaymentMethod.CARD_CHIP)
            .panHash(PanHash.of("a".repeat(64)))
            .stan(SystemTraceAuditNumber.of("123456"))
            .status(TransactionStatus.INITIATED)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private TransactionEntity fullEntity() {
        Instant now = Instant.now();
        TransactionEntity e = new TransactionEntity();
        e.setId(UUID.randomUUID());
        e.setMerchantId("MERCH0000001");
        e.setTerminalId("TERM0001");
        e.setPanHash("a".repeat(64));
        e.setStan("123456");
        e.setAmount(new BigDecimal("6000.00"));
        e.setCurrency("INR");
        e.setNetwork("VISA");
        e.setPaymentMethod("CARD_CHIP");
        e.setStatus("INITIATED");
        e.setIdempotencyKey("idem-key-001");
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }
}
