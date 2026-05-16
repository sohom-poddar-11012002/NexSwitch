package com.payments.domain.model.vo;

// LEARN: GlobalReference — 23-digit ARN is how networks identify transactions for chargebacks and recon
public record AcquirerReferenceNumber(String value) {

    private static final int REQUIRED_LENGTH = 23;

    public AcquirerReferenceNumber {
        if (value == null)
            throw new IllegalArgumentException("AcquirerReferenceNumber must not be null");
        if (value.length() != REQUIRED_LENGTH)
            throw new IllegalArgumentException(
                "AcquirerReferenceNumber (ARN) must be exactly 23 characters, got: " + value.length());
        if (!value.matches("[A-Za-z0-9]{23}"))
            throw new IllegalArgumentException(
                "AcquirerReferenceNumber must be alphanumeric only: " + value);
    }

    public static AcquirerReferenceNumber of(String value) {
        return new AcquirerReferenceNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
