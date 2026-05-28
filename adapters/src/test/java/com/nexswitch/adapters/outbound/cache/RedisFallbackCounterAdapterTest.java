package com.nexswitch.adapters.outbound.cache;

import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.model.vo.TerminalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RedisFallbackCounterAdapterTest {

    // LEARN: PanHash is the full 64-char SHA-256; the adapter truncates to 16 chars for the Redis key.
    //        Using a 64-char string avoids substring-out-of-bounds failures in tests.
    private static final PanHash PAN  = PanHash.of("a".repeat(64));
    private static final TerminalId TERM = new TerminalId("TERM0001");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private RedisFallbackCounterAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new RedisFallbackCounterAdapter(redisTemplate);
    }

    @Test
    void firstFallback_returnsZero_andSetsExpiry() {
        // INCR returns 1 on the first call (key didn't exist before)
        when(valueOps.increment(anyString())).thenReturn(1L);

        int count = adapter.getAndIncrementFallbackCount(PAN, TERM);

        assertThat(count).isEqualTo(0); // count BEFORE increment
        // Expiry should be set on first creation
        verify(redisTemplate).expire(anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void secondFallback_returnsOne_noExtraExpiry() {
        // INCR returns 2 on the second call
        when(valueOps.increment(anyString())).thenReturn(2L);

        int count = adapter.getAndIncrementFallbackCount(PAN, TERM);

        assertThat(count).isEqualTo(1); // count BEFORE increment
        // Expiry only set when afterIncrement==1, not on 2
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void nullRedisResponse_returnsZero() {
        when(valueOps.increment(anyString())).thenReturn(null);

        int count = adapter.getAndIncrementFallbackCount(PAN, TERM);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void keyContainsPanHashPrefixAndTerminalId() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        adapter.getAndIncrementFallbackCount(PAN, TERM);

        verify(valueOps).increment(keyCaptor.capture());
        String key = keyCaptor.getValue();
        assertThat(key).startsWith("fallback:");
        assertThat(key).contains("TERM0001");
        // PAN prefix is first 16 chars of the 64-char hash
        assertThat(key).contains("a".repeat(16));
    }
}
