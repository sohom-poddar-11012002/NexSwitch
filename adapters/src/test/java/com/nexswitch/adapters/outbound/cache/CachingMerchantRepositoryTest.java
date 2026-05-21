package com.nexswitch.adapters.outbound.cache;

import com.nexswitch.domain.model.MerchantProfile;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CachingMerchantRepositoryTest {

    private static final Currency INR       = Currency.getInstance("INR");
    private static final MerchantId MERCH   = MerchantId.of("MERCH0000999");

    @Mock private MerchantRepository        delegate;
    @Mock private StringRedisTemplate       redis;
    @Mock private ValueOperations<String, String> valueOps;

    private CachingMerchantRepository repo;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        repo = new CachingMerchantRepository(delegate, redis);
    }

    @Test
    void findById_hitsDelegate_onCacheMiss() {
        when(valueOps.get("merchant:config:MERCH0000999")).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(delegate.findById(MERCH)).thenReturn(Optional.of(activeMerchant()));

        Optional<MerchantProfile> result = repo.findById(MERCH);

        assertThat(result).isPresent();
        verify(delegate).findById(MERCH);
    }

    @Test
    void findById_doesNotHitDelegate_onL1Hit() {
        // Prime L1 cache by loading from delegate first
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(delegate.findById(MERCH)).thenReturn(Optional.of(activeMerchant()));
        repo.findById(MERCH); // first call fills L1
        reset(delegate);

        repo.findById(MERCH); // second call should hit L1

        verify(delegate, never()).findById(any());
    }

    @Test
    void invalidate_removesFromRedisAndL1() {
        // Prime L1 first
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(delegate.findById(MERCH)).thenReturn(Optional.of(activeMerchant()));
        repo.findById(MERCH);

        repo.invalidate("MERCH0000999");

        verify(redis).delete("merchant:config:MERCH0000999");
        // After invalidation, next call should hit delegate again
        reset(delegate);
        when(delegate.findById(MERCH)).thenReturn(Optional.of(activeMerchant()));
        repo.findById(MERCH);
        verify(delegate).findById(MERCH);
    }

    @Test
    void findById_returnsEmpty_whenMerchantNotFound() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(delegate.findById(MERCH)).thenReturn(Optional.empty());

        Optional<MerchantProfile> result = repo.findById(MERCH);

        assertThat(result).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MerchantProfile activeMerchant() {
        return new MerchantProfile(
                MERCH, "Test Merchant", "5411", MerchantProfile.Status.ACTIVE,
                Money.of("100000.00", INR), Money.of("1000000.00", INR),
                new BigDecimal("0.0150"), new BigDecimal("0.0500"),
                null, null, "merch0000999@payswiff"
        );
    }
}
