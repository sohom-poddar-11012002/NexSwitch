package com.payments.domain.model;

import com.payments.domain.model.vo.Money;
import com.payments.domain.model.vo.PanHash;

import java.time.Instant;
import java.util.Objects;

// LEARN: ContextObject — aggregates inputs for a service call; avoids long parameter lists
public record FraudScoringContext(
        PanHash panHash,
        Money amount,
        String merchantCategory,
        PaymentNetwork network,
        PaymentMethod method,
        Instant transactionTime
) {
    public FraudScoringContext {
        Objects.requireNonNull(panHash, "panHash must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(merchantCategory, "merchantCategory must not be null");
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(transactionTime, "transactionTime must not be null");
    }
}
