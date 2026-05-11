package com.payments.domain.model;

public enum PaymentNetwork {
    VISA,
    MASTERCARD,
    RUPAY,
    UPI;

    public boolean isUpi() {
        return this == UPI;
    }

    public boolean isInternational() {
        return this == VISA || this == MASTERCARD;
    }
}
