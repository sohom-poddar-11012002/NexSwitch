package com.nexswitch.adapters.outbound.bin;

import com.nexswitch.domain.model.BinInfo;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.port.outbound.BinLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// LEARN: TestDouble — StringRedisTemplate is stubbed (not a real Redis connection) so these
//        tests run without Docker. The L1 Caffeine cache is real — we exercise the eviction
//        and promotion paths by observing delegate call counts via Mockito verify().
@ExtendWith(MockitoExtension.class)
class CachingBinLookupAdapterTest {

    @Mock BinLookupPort        delegate;
    @Mock StringRedisTemplate  redis;
    @Mock ValueOperations<String, String> ops;

    CachingBinLookupAdapter adapter;

    static final BinInfo VISA_BIN = new BinInfo(
        "411111", PaymentNetwork.VISA, "HDFC Bank", "CREDIT", "CLASSIC", "IN", "HDFC", false);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        adapter = new CachingBinLookupAdapter(delegate, redis, new ObjectMapper());
    }

    // ── L1 hit: delegate never called after first lookup ─────────────────────

    @Test
    void givenL1Populated_whenLookupCalledTwice_thenDelegateSingleCall() {
        when(ops.get("bin:411111")).thenReturn(null);
        when(ops.setIfAbsent(eq("bin_lock:411111"), eq("1"), any(Duration.class))).thenReturn(true);
        when(delegate.lookup("411111")).thenReturn(Optional.of(VISA_BIN));

        adapter.lookup("411111");
        adapter.lookup("411111"); // second call should hit L1

        verify(delegate, times(1)).lookup("411111");
    }

    // ── Redis hit: delegate not called, result promoted to L1 ────────────────

    @Test
    void givenRedisHit_whenLookup_thenDelegateNotCalled() throws Exception {
        String json = "{\"binPrefix\":\"411111\",\"network\":\"VISA\",\"issuerName\":\"HDFC Bank\","
                    + "\"cardType\":\"CREDIT\",\"cardProduct\":\"CLASSIC\",\"countryCode\":\"IN\","
                    + "\"issuerBank\":\"HDFC\",\"nfsEligible\":false}";
        when(ops.get("bin:411111")).thenReturn(json);

        Optional<BinInfo> result = adapter.lookup("411111");

        assertThat(result).isPresent();
        assertThat(result.get().binPrefix()).isEqualTo("411111");
        verify(delegate, never()).lookup(any());
    }

    // ── Full miss: lock acquired, delegate called, result cached ─────────────

    @Test
    void givenCacheMiss_whenLockAcquired_thenDelegateCalled() {
        when(ops.get("bin:411111")).thenReturn(null);
        when(ops.setIfAbsent(eq("bin_lock:411111"), eq("1"), any(Duration.class))).thenReturn(true);
        when(delegate.lookup("411111")).thenReturn(Optional.of(VISA_BIN));

        Optional<BinInfo> result = adapter.lookup("411111");

        assertThat(result).isPresent();
        verify(delegate).lookup("411111");
        verify(ops).set(eq("bin:411111"), anyString(), any(Duration.class));
        verify(redis).delete("bin_lock:411111");
    }

    // ── Lock contention: falls through to delegate without caching ───────────

    @Test
    void givenLockContention_whenLookup_thenDelegateFallthrough() {
        when(ops.get("bin:411111")).thenReturn(null);
        when(ops.setIfAbsent(eq("bin_lock:411111"), eq("1"), any(Duration.class))).thenReturn(false);
        when(delegate.lookup("411111")).thenReturn(Optional.of(VISA_BIN));

        Optional<BinInfo> result = adapter.lookup("411111");

        assertThat(result).isPresent();
        verify(delegate).lookup("411111");
        // Lock was not won so result must NOT be stored in Redis
        verify(ops, never()).set(eq("bin:411111"), anyString(), any(Duration.class));
    }

    // ── Absent BIN: empty propagated through layers ───────────────────────────

    @Test
    void givenUnknownBin_whenLookup_thenEmptyOptionalReturned() {
        when(ops.get("bin:999999")).thenReturn(null);
        when(ops.setIfAbsent(eq("bin_lock:999999"), eq("1"), any(Duration.class))).thenReturn(true);
        when(delegate.lookup("999999")).thenReturn(Optional.empty());

        Optional<BinInfo> result = adapter.lookup("999999");

        assertThat(result).isEmpty();
        verify(ops).set(eq("bin:999999"), eq("null"), any(Duration.class));
    }

    // ── Redis stores "null" string: returned as empty Optional ───────────────

    @Test
    void givenRedisStoredNull_whenLookup_thenEmptyOptionalReturned() {
        when(ops.get("bin:999999")).thenReturn("null");

        Optional<BinInfo> result = adapter.lookup("999999");

        assertThat(result).isEmpty();
        verify(delegate, never()).lookup(any());
    }
}
