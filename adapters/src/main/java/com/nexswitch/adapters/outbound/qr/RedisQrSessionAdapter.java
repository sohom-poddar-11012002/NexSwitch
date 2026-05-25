package com.nexswitch.adapters.outbound.qr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.NpciTxnId;
import com.nexswitch.domain.model.vo.TxnRef;
import com.nexswitch.domain.port.outbound.QrSessionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

// LEARN: RedisTTL — TTL is derived from domain's expiresAt, not hardcoded here.
//        The domain's QRSession.expiresAt is the authoritative expiry; Redis key lifetime
//        mirrors it so the key auto-deletes when the payment window closes.
@Component
public class RedisQrSessionAdapter implements QrSessionPort {

    private static final Logger log = LoggerFactory.getLogger(RedisQrSessionAdapter.class);
    private static final String PREFIX = "qr:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisQrSessionAdapter(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void save(QRSession session) {
        Duration ttl = Duration.between(Instant.now(), session.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) return;
        redis.opsForValue().set(key(session.txnRef().value()), serialize(session), ttl);
        log.debug("qr.session.saved txnRef={} ttlSeconds={}", session.txnRef(), ttl.toSeconds());
    }

    @Override
    public Optional<QRSession> findByTxnRef(String txnRef) {
        String json = redis.opsForValue().get(key(txnRef));
        if (json == null) {
            log.debug("qr.session.miss txnRef={}", txnRef);
            return Optional.empty();
        }
        return Optional.of(deserialize(json));
    }

    @Override
    public void update(QRSession session) {
        // Preserve remaining TTL by computing against expiresAt
        save(session);
    }

    @Override
    public void delete(String txnRef) {
        redis.delete(key(txnRef));
        log.debug("qr.session.deleted txnRef={}", txnRef);
    }

    private String key(String txnRef) {
        return PREFIX + txnRef;
    }

    private String serialize(QRSession session) {
        try {
            return mapper.writeValueAsString(new SessionDto(
                    session.txnRef().value(),
                    session.merchantId().value(),
                    session.amount().amount().toPlainString(),
                    session.amount().currency().getCurrencyCode(),
                    session.status().name(),
                    session.createdAt().toEpochMilli(),
                    session.expiresAt().toEpochMilli(),
                    session.npciTxnId() != null ? session.npciTxnId().value() : null
            ));
        } catch (Exception e) {
            throw new IllegalStateException("QR session serialization failed", e);
        }
    }

    private QRSession deserialize(String json) {
        try {
            SessionDto dto = mapper.readValue(json, SessionDto.class);
            return QRSession.builder()
                    .txnRef(new TxnRef(dto.txnRef))
                    .merchantId(new MerchantId(dto.merchantId))
                    .amount(Money.of(new BigDecimal(dto.amount),
                            Currency.getInstance(dto.currency)))
                    .status(QRSession.Status.valueOf(dto.status))
                    .createdAt(Instant.ofEpochMilli(dto.createdAtEpochMs))
                    .expiresAt(Instant.ofEpochMilli(dto.expiresAtEpochMs))
                    .npciTxnId(dto.npciTxnId != null ? new NpciTxnId(dto.npciTxnId) : null)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("QR session deserialization failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SessionDto(
            String txnRef,
            String merchantId,
            String amount,
            String currency,
            String status,
            long createdAtEpochMs,
            long expiresAtEpochMs,
            String npciTxnId
    ) {}
}
