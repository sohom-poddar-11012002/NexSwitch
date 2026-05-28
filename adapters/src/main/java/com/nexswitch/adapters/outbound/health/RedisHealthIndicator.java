package com.nexswitch.adapters.outbound.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// LEARN: Redis PING via template is lighter than a separate connection — reuses the Lettuce connection
//        pool; no extra socket is opened. A get() on a sentinel key is the lightest meaningful op:
//        it proves the connection pool, serialization, and server round-trip all work.
@Component("redisCustomHealthIndicator")
public class RedisHealthIndicator implements HealthIndicator {

    private static final String HEALTH_KEY = "__health_ping__";

    private final StringRedisTemplate redisTemplate;

    public RedisHealthIndicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            // LEARN: get() on a non-existent key returns null — that's fine; the point is the
            //        round-trip completing without an exception proves Redis is reachable.
            redisTemplate.opsForValue().get(HEALTH_KEY);
            return Health.up().withDetail("ping", "OK").build();
        } catch (Exception ex) {
            return Health.down().withDetail("error", ex.getMessage()).build();
        }
    }
}
