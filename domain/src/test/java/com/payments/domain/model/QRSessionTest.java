package com.payments.domain.model;

import com.payments.domain.model.vo.MerchantId;
import com.payments.domain.model.vo.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

class QRSessionTest {

    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void createsQRSessionWithRequiredFields() {
        QRSession session = QRSession.builder()
            .txnRef("TXN20260511MERCH0000999001")
            .merchantId(MerchantId.of("MERCH0000999"))
            .amount(Money.of("6000.00", INR))
            .status(QRSession.Status.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

        assertThat(session.txnRef()).isEqualTo("TXN20260511MERCH0000999001");
        assertThat(session.status()).isEqualTo(QRSession.Status.PENDING);
    }

    @Test
    void isExpiredWhenExpiryInPast() {
        QRSession session = QRSession.builder()
            .txnRef("TXN20260511MERCH0000999001")
            .merchantId(MerchantId.of("MERCH0000999"))
            .amount(Money.of("500.00", INR))
            .status(QRSession.Status.PENDING)
            .createdAt(Instant.now().minusSeconds(400))
            .expiresAt(Instant.now().minusSeconds(100))
            .build();

        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void isNotExpiredWhenExpiryInFuture() {
        QRSession session = QRSession.builder()
            .txnRef("TXN20260511MERCH0000999001")
            .merchantId(MerchantId.of("MERCH0000999"))
            .amount(Money.of("500.00", INR))
            .status(QRSession.Status.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void withStatusReturnsNewInstance() {
        QRSession original = QRSession.builder()
            .txnRef("TXN20260511MERCH0000999001")
            .merchantId(MerchantId.of("MERCH0000999"))
            .amount(Money.of("500.00", INR))
            .status(QRSession.Status.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

        QRSession completed = original.withStatus(QRSession.Status.COMPLETED);
        assertThat(completed.status()).isEqualTo(QRSession.Status.COMPLETED);
        assertThat(original.status()).isEqualTo(QRSession.Status.PENDING);
    }

    @Test
    void statusEnumHasFourValues() {
        assertThat(QRSession.Status.values()).hasSize(4);
        assertThat(QRSession.Status.values()).containsExactlyInAnyOrder(
            QRSession.Status.PENDING,
            QRSession.Status.COMPLETED,
            QRSession.Status.EXPIRED,
            QRSession.Status.FAILED
        );
    }

    @Test
    void throwsWhenTxnRefIsNull() {
        assertThatThrownBy(() -> QRSession.builder()
            .txnRef(null)
            .merchantId(MerchantId.of("MERCH0000999"))
            .amount(Money.of("500.00", INR))
            .status(QRSession.Status.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
