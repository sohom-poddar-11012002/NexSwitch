package com.nexswitch.domain.service;

import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class QRSessionManagerTest {

    private static final Currency INR = Currency.getInstance("INR");
    private static final MerchantId MERCHANT = new MerchantId("MERCH0000999");
    private static final Money AMOUNT = Money.of(new BigDecimal("6000.00"), INR);

    private QRSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new QRSessionManager(5);
    }

    // ── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_producesSessionWithCorrectFields() {
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001");

        assertThat(session.merchantId()).isEqualTo(MERCHANT);
        assertThat(session.amount()).isEqualTo(AMOUNT);
        assertThat(session.status()).isEqualTo(QRSession.Status.PENDING);
        assertThat(session.npciTxnId()).isNull();
        assertThat(session.txnRef()).startsWith("TXN");
        assertThat(session.expiresAt()).isAfter(session.createdAt());
    }

    @Test
    void create_txnRefContainsMerchantId() {
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001");
        assertThat(session.txnRef()).contains("MERCH0000999");
    }

    @Test
    void create_sessionTtlIsConfigured() {
        Instant before = Instant.now();
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001");
        Instant after = Instant.now();

        // expiresAt should be ~5 minutes after createdAt
        long ttlSeconds = session.expiresAt().getEpochSecond() - session.createdAt().getEpochSecond();
        assertThat(ttlSeconds).isBetween(299L, 301L);
        assertThat(session.createdAt()).isBetween(before, after);
    }

    @Test
    void create_sequentialCallsProduceUniqueTxnRefs() {
        QRSession a = manager.create(MERCHANT, AMOUNT, "order-001");
        QRSession b = manager.create(MERCHANT, AMOUNT, "order-002");
        assertThat(a.txnRef()).isNotEqualTo(b.txnRef());
    }

    @Test
    void create_rejectsZeroTtl() {
        assertThatThrownBy(() -> new QRSessionManager(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── buildUpiString ─────────────────────────────────────────────────────────

    @Test
    void buildUpiString_containsAllMandatoryComponents() {
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001");
        String upi = manager.buildUpiString(session, "merchant@payswiff", "Test Merchant");

        assertThat(upi).startsWith("upi://pay?");
        assertThat(upi).contains("pa=merchant%40payswiff");
        assertThat(upi).contains("pn=Test+Merchant");
        assertThat(upi).contains("tr=" + session.txnRef());
        assertThat(upi).contains("am=6000.00");
        assertThat(upi).contains("cu=INR");
    }

    @Test
    void buildUpiString_amountIsFormattedToTwoDecimalPlaces() {
        Money exactAmount = Money.of(new BigDecimal("500"), INR);
        QRSession session = manager.create(MERCHANT, exactAmount, "order-001");
        String upi = manager.buildUpiString(session, "m@payswiff", "Merchant");

        assertThat(upi).contains("am=500.00");
    }

    // ── isActive ───────────────────────────────────────────────────────────────

    @Test
    void isActive_returnsTrueForPendingNonExpiredSession() {
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001");
        assertThat(manager.isActive(session)).isTrue();
    }

    @Test
    void isActive_returnsFalseForCompletedSession() {
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001")
                .withStatus(QRSession.Status.COMPLETED);
        assertThat(manager.isActive(session)).isFalse();
    }

    @Test
    void isActive_returnsFalseForExpiredStatus() {
        QRSession session = manager.create(MERCHANT, AMOUNT, "order-001")
                .withStatus(QRSession.Status.EXPIRED);
        assertThat(manager.isActive(session)).isFalse();
    }

    @Test
    void isActive_returnsFalseForSessionPastExpiryTime() {
        // Build a session that already expired
        QRSession expired = QRSession.builder()
                .txnRef("TXN-EXPIRED")
                .merchantId(MERCHANT)
                .amount(AMOUNT)
                .status(QRSession.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        assertThat(manager.isActive(expired)).isFalse();
    }
}
