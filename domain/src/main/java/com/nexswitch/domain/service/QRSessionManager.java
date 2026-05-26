package com.nexswitch.domain.service;

import com.nexswitch.domain.model.QRSession;
import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.model.vo.TxnRef;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

// LEARN: DomainService — TTL and STAN generation are domain concepts; Redis is the adapter
public class QRSessionManager {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final long sessionTtlMinutes;
    private final Clock clock;
    private final AtomicInteger seq = new AtomicInteger(0);

    public QRSessionManager(long sessionTtlMinutes) {
        this(sessionTtlMinutes, Clock.systemUTC());
    }

    public QRSessionManager(long sessionTtlMinutes, Clock clock) {
        if (sessionTtlMinutes <= 0) throw new IllegalArgumentException("sessionTtlMinutes must be positive");
        this.sessionTtlMinutes = sessionTtlMinutes;
        this.clock = clock;
    }

    public QRSession create(MerchantId merchantId, Money amount, String orderId) {
        Instant now = Instant.now(clock);
        TxnRef txnRef = TxnRef.of("TXN" + TIMESTAMP_FMT.format(now)
                + merchantId.value()
                + String.format("%04d", seq.incrementAndGet() % 10_000));
        return QRSession.builder()
                .txnRef(txnRef)
                .merchantId(merchantId)
                .amount(amount)
                .status(QRSession.Status.PENDING)
                .createdAt(now)
                .expiresAt(now.plus(sessionTtlMinutes, ChronoUnit.MINUTES))
                .build();
    }

    // Per §21.1: upi://pay?pa={vpa}&pn={name}&tr={txnRef}&am={amount}&cu={currency}
    public String buildUpiString(QRSession session, String vpa, String payeeName) {
        String amount = session.amount().amount()
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
        return "upi://pay"
                + "?pa=" + encode(vpa)
                + "&pn=" + encode(payeeName)
                + "&tr=" + encode(session.txnRef().value())
                + "&am=" + amount
                + "&cu=" + session.amount().currency().getCurrencyCode();
    }

    public boolean isActive(QRSession session) {
        return session.status() == QRSession.Status.PENDING && !session.isExpired(clock);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
