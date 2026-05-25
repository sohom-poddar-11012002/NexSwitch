package com.nexswitch.adapters.outbound.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexswitch.domain.model.Terminal;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.TerminalId;
import com.nexswitch.domain.port.outbound.TerminalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

// LEARN: L1/L2 cache hierarchy for terminal lookup — same Caffeine+Redis+SETNX pattern as
//        CachingBinLookupAdapter. Terminal state changes rarely (config ops) so a 30s L1 /
//        5min L2 TTL is safe. On Redis outage, both L2 reads and SETNX fall through to the
//        Postgres delegate — degraded-mode caching rather than full service failure.
@Primary
@Component
public class CachingTerminalRepository implements TerminalRepository {

    private static final Logger log = LoggerFactory.getLogger(CachingTerminalRepository.class);

    private static final Duration REDIS_BASE_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL       = Duration.ofSeconds(2);
    private static final String   KEY_PREFIX     = "terminal:";
    private static final String   LOCK_PREFIX    = "terminal_lock:";

    private final TerminalRepository                      delegate;
    private final StringRedisTemplate                     redis;
    private final Cache<String, Optional<Terminal>>       l1;
    private final ObjectMapper                            mapper;

    public CachingTerminalRepository(
            @Qualifier("postgresTerminalRepository") TerminalRepository delegate,
            StringRedisTemplate redis) {
        this.delegate = delegate;
        this.redis    = redis;
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(5_000)
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public Optional<Terminal> findById(TerminalId id) {
        String terminalId = id.value();

        Optional<Terminal> l1Hit = l1.getIfPresent(terminalId);
        if (l1Hit != null) {
            log.debug("terminal.cache.l1_hit terminalId={}", terminalId);
            return l1Hit;
        }

        String redisKey = KEY_PREFIX + terminalId;
        try {
            String json = redis.opsForValue().get(redisKey);
            if (json != null) {
                log.debug("terminal.cache.l2_hit terminalId={}", terminalId);
                Optional<Terminal> result = deserialize(json);
                l1.put(terminalId, result);
                return result;
            }
        } catch (Exception e) {
            // LEARN: Redis fallback — if Redis is down, we skip L2 and fall through to
            //        Postgres. One slow DB call is far better than a full auth failure.
            log.warn("terminal.cache.redis_down terminalId={} — falling through to DB", terminalId, e);
            return delegate.findById(id);
        }

        String lockKey = LOCK_PREFIX + terminalId;
        try {
            Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    Optional<Terminal> result = delegate.findById(id);
                    redis.opsForValue().set(redisKey, serialize(result), jitteredTtl());
                    l1.put(terminalId, result);
                    log.debug("terminal.cache.miss_filled terminalId={}", terminalId);
                    return result;
                } finally {
                    redis.delete(lockKey);
                }
            }
        } catch (Exception e) {
            log.warn("terminal.cache.redis_lock_failed terminalId={} — going to DB", terminalId, e);
            return delegate.findById(id);
        }

        log.debug("terminal.cache.lock_contention terminalId={}", terminalId);
        return delegate.findById(id);
    }

    private Duration jitteredTtl() {
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble(0.2);
        return Duration.ofMillis((long) (REDIS_BASE_TTL.toMillis() * factor));
    }

    private String serialize(Optional<Terminal> opt) {
        try {
            if (opt.isEmpty()) return "null";
            Terminal t = opt.get();
            return mapper.writeValueAsString(new TerminalDto(
                    t.terminalId().value(),
                    t.merchantId().value(),
                    t.status().name()
            ));
        } catch (Exception e) {
            log.warn("terminal.cache.serialize_fail", e);
            return "null";
        }
    }

    private Optional<Terminal> deserialize(String json) {
        if ("null".equals(json)) return Optional.empty();
        try {
            TerminalDto dto = mapper.readValue(json, TerminalDto.class);
            return Optional.of(new Terminal(
                    TerminalId.of(dto.terminalId),
                    MerchantId.of(dto.merchantId),
                    Terminal.Status.valueOf(dto.status)
            ));
        } catch (Exception e) {
            log.warn("terminal.cache.deserialize_fail", e);
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TerminalDto(String terminalId, String merchantId, String status) {}
}
