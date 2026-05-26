package com.nexswitch.adapters.outbound.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

// LEARN: L1/L2 caching for merchant profiles — same pattern as CachingBinLookupAdapter.
//        Merchants change rarely (config updates), so TTL can be longer: 30s L1, 5min L2.
//        Cache invalidation on merchant.config.updated Kafka event ensures consistency
//        without relying solely on TTL (see MerchantCacheInvalidationListener).
@Primary
@Component
public class CachingMerchantRepository implements MerchantRepository {

    private static final Logger log = LoggerFactory.getLogger(CachingMerchantRepository.class);

    private static final Duration REDIS_BASE_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL       = Duration.ofSeconds(2);
    private static final String   KEY_PREFIX     = "merchant:config:";
    private static final String   LOCK_PREFIX    = "merchant_lock:";

    private final MerchantRepository                        delegate;
    private final StringRedisTemplate                       redis;
    private final Cache<String, Optional<MerchantProfile>>  l1;
    private final ObjectMapper                              mapper;

    public CachingMerchantRepository(
            @Qualifier("postgresMerchantRepository") MerchantRepository delegate,
            StringRedisTemplate redis,
            ObjectMapper mapper) {
        this.delegate = delegate;
        this.redis    = redis;
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(1_000)
                .build();
        this.mapper = mapper;
    }

    @Override
    public Optional<MerchantProfile> findById(MerchantId id) {
        String merchantId = id.value();

        // L1: JVM-local Caffeine hit
        Optional<MerchantProfile> l1Hit = l1.getIfPresent(merchantId);
        if (l1Hit != null) {
            log.debug("merchant.cache.l1_hit merchantId={}", merchantId);
            return l1Hit;
        }

        // L2: Redis hit
        String redisKey = KEY_PREFIX + merchantId;
        try {
            String json = redis.opsForValue().get(redisKey);
            if (json != null) {
                log.debug("merchant.cache.l2_hit merchantId={}", merchantId);
                Optional<MerchantProfile> result = deserialize(json);
                l1.put(merchantId, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("merchant.cache.redis_down merchantId={} — falling through to DB", merchantId, e);
            return delegate.findById(id);
        }

        // Cache miss — SETNX lock prevents stampede
        // LEARN: Only one thread races the DB; others fall through to delegate directly.
        //        Safe for merchant reads: the profile is deterministic and read-only here.
        String lockKey = LOCK_PREFIX + merchantId;
        try {
            Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    Optional<MerchantProfile> result = delegate.findById(id);
                    redis.opsForValue().set(redisKey, serialize(result), jitteredTtl());
                    l1.put(merchantId, result);
                    log.debug("merchant.cache.miss_filled merchantId={}", merchantId);
                    return result;
                } finally {
                    redis.delete(lockKey);
                }
            }
        } catch (Exception e) {
            log.warn("merchant.cache.redis_lock_failed merchantId={} — going to DB", merchantId, e);
            return delegate.findById(id);
        }

        log.debug("merchant.cache.lock_contention merchantId={}", merchantId);
        return delegate.findById(id);
    }

    public void invalidate(String merchantId) {
        l1.invalidate(merchantId);
        redis.delete(KEY_PREFIX + merchantId);
        log.debug("merchant.cache.invalidated merchantId={}", merchantId);
    }

    // LEARN: TTL jitter staggers Redis key expiry across service instances; avoids
    //        all replicas racing to refill the same key simultaneously after expiry.
    private Duration jitteredTtl() {
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble(0.2);
        return Duration.ofMillis((long) (REDIS_BASE_TTL.toMillis() * factor));
    }

    private String serialize(Optional<MerchantProfile> opt) {
        try {
            if (opt.isEmpty()) return "null";
            MerchantProfile p = opt.get();
            Currency inr = p.perTransactionLimit().currency();
            return mapper.writeValueAsString(new MerchantDto(
                    p.merchantId().value(),
                    p.name(),
                    p.mcc(),
                    p.status().name(),
                    p.perTransactionLimit().amount().toPlainString(),
                    p.dailyLimit().amount().toPlainString(),
                    inr.getCurrencyCode(),
                    p.mdrPercentage().toPlainString(),
                    p.reservePercentage().toPlainString(),
                    p.webhookUrl(),
                    p.webhookSecret(),
                    p.vpa()
            ));
        } catch (Exception e) {
            log.warn("merchant.cache.serialize_fail", e);
            return "null";
        }
    }

    private Optional<MerchantProfile> deserialize(String json) {
        if ("null".equals(json)) return Optional.empty();
        try {
            MerchantDto dto = mapper.readValue(json, MerchantDto.class);
            Currency ccy = Currency.getInstance(dto.currency);
            return Optional.of(new MerchantProfile(
                    new MerchantId(dto.merchantId),
                    dto.name,
                    dto.mcc,
                    MerchantProfile.Status.valueOf(dto.status),
                    Money.of(new BigDecimal(dto.perTxnLimit), ccy),
                    Money.of(new BigDecimal(dto.dailyLimit), ccy),
                    new BigDecimal(dto.mdrPercentage),
                    new BigDecimal(dto.reservePercentage),
                    dto.webhookUrl,
                    dto.webhookSecret,
                    dto.vpa
            ));
        } catch (Exception e) {
            log.warn("merchant.cache.deserialize_fail", e);
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MerchantDto(
            String merchantId,
            String name,
            String mcc,
            String status,
            String perTxnLimit,
            String dailyLimit,
            String currency,
            String mdrPercentage,
            String reservePercentage,
            String webhookUrl,
            String webhookSecret,
            String vpa
    ) {}
}
