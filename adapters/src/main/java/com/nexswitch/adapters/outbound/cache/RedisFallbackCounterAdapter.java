package com.nexswitch.adapters.outbound.cache;

import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.model.vo.TerminalId;
import com.nexswitch.domain.port.outbound.FallbackCounterPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

// LEARN: Redis INCR is atomic — no read-modify-write race condition; under high concurrency
//        (multiple auth threads), all see consistent counts. EXPIRE is set on first creation
//        so the key auto-deletes at midnight, giving a fresh per-day window per scheme rules.
@Component
public class RedisFallbackCounterAdapter implements FallbackCounterPort {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public RedisFallbackCounterAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int getAndIncrementFallbackCount(PanHash panHash, TerminalId terminalId) {
        String key = buildKey(panHash, terminalId);
        // LEARN: INCR returns the value AFTER increment; we subtract 1 to get the count BEFORE.
        //        First call: null → 0, then INCR returns 1 → currentCount = 0 (allow first fallback).
        Long afterIncrement = redisTemplate.opsForValue().increment(key);
        if (afterIncrement == null) return 0;
        long countBeforeIncrement = afterIncrement - 1;
        // Set TTL only on first creation (when count transitions from 0→1)
        if (afterIncrement == 1) {
            redisTemplate.expire(key, TTL);
        }
        return (int) countBeforeIncrement;
    }

    private static String buildKey(PanHash panHash, TerminalId terminalId) {
        return String.format("fallback:%s:%s:%s",
                panHash.value().substring(0, 16), // truncate for key length; still unique enough per day
                terminalId.value(),
                LocalDate.now());
    }
}
