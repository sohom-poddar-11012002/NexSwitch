package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.MerchantId;

import java.time.LocalDate;
import java.util.UUID;

// LEARN: Network tokenization (EMVCo Payment Tokenization) — Visa Token Service (VTS) and
//        Mastercard Digital Enablement Service (MDES) replace the PAN with a merchant-scoped token.
//        A stolen token is useless outside the registered merchant domain.
public record NetworkToken(
        UUID id,
        MerchantId merchantId,
        String networkTokenValue,
        String lastFour,
        PaymentNetwork network,
        LocalDate expiry,
        TokenStatus status
) {
    public NetworkToken {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (merchantId == null) throw new IllegalArgumentException("merchantId must not be null");
        if (networkTokenValue == null || networkTokenValue.isBlank())
            throw new IllegalArgumentException("networkTokenValue must not be blank");
        if (lastFour == null || !lastFour.matches("\\d{4}"))
            throw new IllegalArgumentException("lastFour must be 4 digits");
        if (network == null) throw new IllegalArgumentException("network must not be null");
        if (expiry == null) throw new IllegalArgumentException("expiry must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
    }

    public enum TokenStatus {
        ACTIVE, SUSPENDED, DELETED
    }
}
