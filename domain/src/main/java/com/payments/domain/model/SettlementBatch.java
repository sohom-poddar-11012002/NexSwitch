package com.payments.domain.model;

import com.payments.domain.model.vo.Money;

import java.time.LocalDate;
import java.util.UUID;

public record SettlementBatch(
    UUID id,
    PaymentNetwork network,
    LocalDate batchDate,
    int transactionCount,
    Money grossAmount,
    Status status
) {
    public SettlementBatch {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (network == null) throw new IllegalArgumentException("network must not be null");
        if (batchDate == null) throw new IllegalArgumentException("batchDate must not be null");
        if (transactionCount < 0) throw new IllegalArgumentException("transactionCount must not be negative");
        if (grossAmount == null) throw new IllegalArgumentException("grossAmount must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
    }

    public enum Status { GENERATED, VALIDATED, SUBMITTED, ACKNOWLEDGED, FAILED }
}
