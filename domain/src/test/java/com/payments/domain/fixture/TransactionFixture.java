package com.payments.domain.fixture;

import com.payments.domain.model.*;
import com.payments.domain.model.vo.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public final class TransactionFixture {

    private static final Currency INR = Currency.getInstance("INR");

    private TransactionFixture() {}

    public static Transaction initiated() {
        return withStatus(TransactionStatus.INITIATED);
    }

    public static Transaction withStatus(TransactionStatus status) {
        return Transaction.builder()
            .id(UUID.randomUUID())
            .merchantId(MerchantId.of("MERCH0000999"))
            .terminalId(TerminalId.of("TERM0042"))
            .amount(Money.of("6000.00", INR))
            .network(PaymentNetwork.VISA)
            .paymentMethod(PaymentMethod.CARD_CHIP)
            .panHash(PanHash.of("a".repeat(64)))
            .stan(SystemTraceAuditNumber.of("000042"))
            .status(status)
            .createdAt(Instant.now())
            .build();
    }
}
