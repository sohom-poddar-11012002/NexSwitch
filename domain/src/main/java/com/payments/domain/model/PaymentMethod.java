package com.payments.domain.model;

// LEARN: SmartEnum — isCard() / isUpi() keep routing logic out of switch statements in callers
public enum PaymentMethod {
    CARD_CHIP,
    CONTACTLESS,
    UPI_QR,
    UPI_COLLECT;

    public boolean isCard() {
        return this == CARD_CHIP || this == CONTACTLESS;
    }

    public boolean isUpi() {
        return this == UPI_QR || this == UPI_COLLECT;
    }
}
