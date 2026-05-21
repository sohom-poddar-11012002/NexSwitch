package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record InitiateCollectCommand(
        MerchantId merchantId,
        String     payerVpa,
        Money      amount,
        String     orderId,
        int        expirySeconds
) {
    public InitiateCollectCommand {
        Objects.requireNonNull(merchantId, "merchantId required");
        Objects.requireNonNull(amount, "amount required");
        if (payerVpa == null || payerVpa.isBlank()) throw new IllegalArgumentException("payerVpa required");
        if (orderId  == null || orderId.isBlank())  throw new IllegalArgumentException("orderId required");
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be positive");
        if (expirySeconds <= 0) throw new IllegalArgumentException("expirySeconds must be positive");
    }
}
