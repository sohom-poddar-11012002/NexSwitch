package com.nexswitch.adapters.outbound.qr;

import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.NpciTxnId;
import com.nexswitch.domain.model.vo.TxnRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisQrSessionAdapterTest {

    private static final Currency INR     = Currency.getInstance("INR");
    private static final MerchantId MERCH = new MerchantId("MERCH0000999");

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private RedisQrSessionAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        adapter = new RedisQrSessionAdapter(redis, new ObjectMapper());
    }

    @Test
    void save_storesJsonWithTtlDerivedFromExpiresAt() {
        QRSession session = pendingSession("TXN001", Instant.now().plusSeconds(300));

        adapter.save(session);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq("qr:session:TXN001"), anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toSeconds()).isGreaterThan(295L);
    }

    @Test
    void save_skipsExpiredSession() {
        QRSession session = pendingSession("TXN_EXPIRED", Instant.now().minusSeconds(1));

        adapter.save(session);

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void findByTxnRef_returnsEmpty_whenKeyMissing() {
        when(valueOps.get("qr:session:TXN_MISS")).thenReturn(null);

        Optional<QRSession> result = adapter.findByTxnRef("TXN_MISS");

        assertThat(result).isEmpty();
    }

    @Test
    void findByTxnRef_deserializesCorrectly() {
        QRSession session = pendingSession("TXN002", Instant.now().plusSeconds(300));
        adapter.save(session);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), jsonCaptor.capture(), any());
        when(valueOps.get("qr:session:TXN002")).thenReturn(jsonCaptor.getValue());

        Optional<QRSession> found = adapter.findByTxnRef("TXN002");

        assertThat(found).isPresent();
        assertThat(found.get().txnRef()).isEqualTo(new TxnRef("TXN002"));
        assertThat(found.get().merchantId()).isEqualTo(MERCH);
        assertThat(found.get().amount().amount()).isEqualByComparingTo("6000.00");
        assertThat(found.get().status()).isEqualTo(QRSession.Status.PENDING);
    }

    @Test
    void update_overwritesExistingKey() {
        QRSession original = pendingSession("TXN003", Instant.now().plusSeconds(200));
        QRSession completed = original.withStatus(QRSession.Status.COMPLETED)
                .withNpciTxnId(new NpciTxnId("NPCI123"));

        adapter.update(completed);

        verify(valueOps).set(eq("qr:session:TXN003"), anyString(), any());
    }

    @Test
    void delete_removesKey() {
        adapter.delete("TXN004");

        verify(redis).delete("qr:session:TXN004");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QRSession pendingSession(String txnRef, Instant expiresAt) {
        return QRSession.builder()
                .txnRef(new TxnRef(txnRef))
                .merchantId(MERCH)
                .amount(Money.of(new BigDecimal("6000.00"), INR))
                .status(QRSession.Status.PENDING)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }
}
