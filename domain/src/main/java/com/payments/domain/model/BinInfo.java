package com.payments.domain.model;

public record BinInfo(
    String binPrefix,
    PaymentNetwork network,
    String issuerName,
    String cardType,
    String cardProduct,
    String countryCode,
    String issuerBank,    // e.g. "CANARA", "SBI", "HDFC" — for on-us routing detection
    boolean nfsEligible   // true for RuPay + co-badged domestic debit → route via NPCI NFS
) {
    public BinInfo {
        if (binPrefix == null || binPrefix.isBlank())
            throw new IllegalArgumentException("binPrefix must not be blank");
        if (binPrefix.length() < 6 || binPrefix.length() > 8)
            throw new IllegalArgumentException("binPrefix must be 6–8 characters: " + binPrefix);
        if (network == null)
            throw new IllegalArgumentException("network must not be null");
    }
}
