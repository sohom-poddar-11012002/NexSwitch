package com.nexswitch.adapters.outbound.idempotency;

import com.nexswitch.domain.port.outbound.IdempotencyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// LEARN: SETNX — "SET if Not eXists" is Redis's atomic check-and-set.
//        SET key value NX EX <ttl> succeeds only if the key is absent, then sets TTL atomically.
//        No WATCH/MULTI/EXEC needed — the single-command atomicity prevents two threads from
//        both believing they acquired the lock (split-brain under concurrent retransmissions).
@Component
public class RedisIdempotencyAdapter implements IdempotencyPort {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyAdapter.class);

    private static final String PREFIX = "idempotency:";

    private final StringRedisTemplate redis;

    public RedisIdempotencyAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        try {
            // setIfAbsent = SET NX EX — returns true only on first call; false on retransmission
            Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            // LEARN: Idempotency fallback — when Redis is down we allow the request through rather
            //        than blocking all traffic. Duplicate processing is rare and recoverable;
            //        a full auth outage due to Redis unavailability is not.
            log.warn("idempotency.redis_down key={} — allowing through (degraded mode)", key, e);
            return true;
        }
    }
}
