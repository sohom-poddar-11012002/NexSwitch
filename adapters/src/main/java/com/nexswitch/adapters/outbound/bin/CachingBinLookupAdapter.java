package com.nexswitch.adapters.outbound.bin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexswitch.domain.model.BinInfo;
import com.nexswitch.domain.port.outbound.BinLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

// LEARN: L1/L2 cache hierarchy — Caffeine (in-process, sub-μs) guards Redis (cross-process, ~1ms).
//        On cache miss a Redis SETNX lock lets only one thread hit the DB; others fall through
//        to the delegate rather than piling up — this eliminates the thundering herd / cache
//        stampede that would otherwise hammer the DB when a hot BIN key expires simultaneously.
@Primary
@Component
public class CachingBinLookupAdapter implements BinLookupPort {

    private static final Logger log = LoggerFactory.getLogger(CachingBinLookupAdapter.class);

    private static final Duration REDIS_BASE_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL       = Duration.ofSeconds(2);
    private static final String   KEY_PREFIX     = "bin:";
    private static final String   LOCK_PREFIX    = "bin_lock:";

    private final BinLookupPort                      delegate;
    private final StringRedisTemplate                redis;
    private final Cache<String, Optional<BinInfo>>   l1;
    private final ObjectMapper                       mapper;

    public CachingBinLookupAdapter(
            @Qualifier("mockBinLookupAdapter") BinLookupPort delegate,
            StringRedisTemplate redis) {
        this.delegate = delegate;
        this.redis    = redis;
        // LEARN: expireAfterWrite resets TTL on write, not on read — prevents stale hot keys
        //        from living forever (expireAfterAccess would keep them alive indefinitely).
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10_000)
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public Optional<BinInfo> lookup(String bin6) {
        // L1: JVM-local Caffeine hit
        Optional<BinInfo> l1hit = l1.getIfPresent(bin6);
        if (l1hit != null) {
            log.debug("bin.cache.l1_hit bin6={}", bin6);
            return l1hit;
        }

        // L2: Redis hit
        String redisKey = KEY_PREFIX + bin6;
        try {
            String json = redis.opsForValue().get(redisKey);
            if (json != null) {
                log.debug("bin.cache.l2_hit bin6={}", bin6);
                Optional<BinInfo> result = deserialize(json);
                l1.put(bin6, result);
                return result;
            }
        } catch (Exception e) {
            // LEARN: Redis fallback — a Redis outage must not take down the auth path.
            //        One Postgres read per request is acceptable in degraded mode.
            log.warn("bin.cache.redis_down bin6={} — falling through to DB", bin6, e);
            return delegate.lookup(bin6);
        }

        // Cache miss — acquire distributed lock to prevent cache stampede
        // LEARN: SETNX (SET if Not eXists) is the Redis atomic primitive for distributed locks.
        //        Only the thread that wins the SETNX races the DB; all others fall through to
        //        the delegate directly (safe here — BIN lookups are deterministic reads).
        String lockKey = LOCK_PREFIX + bin6;
        try {
            Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    Optional<BinInfo> result = delegate.lookup(bin6);
                    redis.opsForValue().set(redisKey, serialize(result), jitteredTtl());
                    l1.put(bin6, result);
                    log.debug("bin.cache.miss_filled bin6={}", bin6);
                    return result;
                } finally {
                    redis.delete(lockKey);
                }
            }
        } catch (Exception e) {
            log.warn("bin.cache.redis_lock_failed bin6={} — going to DB", bin6, e);
            return delegate.lookup(bin6);
        }

        // Lock contention — another thread is fetching; go straight to delegate
        log.debug("bin.cache.lock_contention bin6={}", bin6);
        return delegate.lookup(bin6);
    }

    // LEARN: TTL jitter (±10%) staggers Redis key expiry across multiple service instances,
    //        preventing all of them from racing to refill the same key at the same moment.
    private Duration jitteredTtl() {
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble(0.2);
        return Duration.ofMillis((long) (REDIS_BASE_TTL.toMillis() * factor));
    }

    private String serialize(Optional<BinInfo> opt) {
        try {
            return opt.isPresent() ? mapper.writeValueAsString(opt.get()) : "null";
        } catch (Exception e) {
            return "null";
        }
    }

    private Optional<BinInfo> deserialize(String json) {
        if ("null".equals(json)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(json, BinInfo.class));
        } catch (Exception e) {
            log.warn("bin.cache.deserialize_fail", e);
            return Optional.empty();
        }
    }
}
