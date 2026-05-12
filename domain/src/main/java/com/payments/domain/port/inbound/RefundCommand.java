package com.payments.domain.port.inbound;

import com.payments.domain.model.vo.Money;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RefundCommand(
        UUID originalTransactionId,
        Money refundAmount,
        String reason
) {
    public RefundCommand {
        Objects.requireNonNull(originalTransactionId, "originalTransactionId must not be null");
        Objects.requireNonNull(refundAmount, "refundAmount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (refundAmount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("refundAmount must be positive");
        }
    }
}
