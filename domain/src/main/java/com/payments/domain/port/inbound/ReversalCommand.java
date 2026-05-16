package com.payments.domain.port.inbound;

import com.payments.domain.model.vo.Money;
import com.payments.domain.model.vo.SystemTraceAuditNumber;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// LEARN: CommandObject — reversalAmount <= originalAmount validated at construction
public record ReversalCommand(
        UUID originalTransactionId,
        SystemTraceAuditNumber originalStan,
        Money originalAmount,
        Money reversalAmount
) {
    public ReversalCommand {
        Objects.requireNonNull(originalTransactionId, "originalTransactionId must not be null");
        Objects.requireNonNull(originalStan, "originalStan must not be null");
        Objects.requireNonNull(originalAmount, "originalAmount must not be null");
        Objects.requireNonNull(reversalAmount, "reversalAmount must not be null");
        if (reversalAmount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("reversalAmount must be positive");
        }
        if (reversalAmount.amount().compareTo(originalAmount.amount()) > 0) {
            throw new IllegalArgumentException("reversalAmount must not exceed originalAmount");
        }
    }
}
