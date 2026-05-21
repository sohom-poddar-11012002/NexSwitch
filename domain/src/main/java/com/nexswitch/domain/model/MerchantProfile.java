package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.MerchantId;
import com.nexswitch.domain.model.vo.Money;

import java.math.BigDecimal;

// LEARN: RichDomainModel — isActive() and limit checks live here, not in service; domain carries behaviour
public record MerchantProfile(
    MerchantId merchantId,
    String name,
    String mcc,
    Status status,
    Money perTransactionLimit,
    Money dailyLimit,
    BigDecimal mdrPercentage,
    BigDecimal reservePercentage,
    String webhookUrl,
    String webhookSecret,
    String vpa               // nullable — UPI virtual payment address for QR payments
) {
    public MerchantProfile {
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (mcc == null || mcc.isBlank()) throw new IllegalArgumentException("mcc must not be blank");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (perTransactionLimit == null) throw new IllegalArgumentException("perTransactionLimit must not be null");
        if (dailyLimit == null) throw new IllegalArgumentException("dailyLimit must not be null");
        if (mdrPercentage == null) throw new IllegalArgumentException("mdrPercentage must not be null");
        if (reservePercentage == null) throw new IllegalArgumentException("reservePercentage must not be null");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public enum Status { ACTIVE, SUSPENDED, TERMINATED }
}
