package com.nexswitch.domain.model;

// LEARN: SmartEnum — isCard() / isUpi() keep routing logic out of switch statements in callers
public enum PaymentMethod {
    CARD_CHIP,
    CONTACTLESS,
    MAGSTRIPE,
    UPI_QR,
    UPI_COLLECT;

    public boolean isCard() {
        return this == CARD_CHIP || this == CONTACTLESS || this == MAGSTRIPE;
    }

    public boolean isUpi() {
        return this == UPI_QR || this == UPI_COLLECT;
    }
}
