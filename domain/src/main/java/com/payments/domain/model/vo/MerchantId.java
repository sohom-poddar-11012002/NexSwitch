package com.payments.domain.model.vo;

// LEARN: WrapperType — distinct type prevents passing TerminalId where MerchantId expected (type safety)
public record MerchantId(String value) {

    private static final int MAX_LENGTH = 15;
    private static final int MIN_LENGTH = 6;

    public MerchantId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("MerchantId must not be null or blank");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                "MerchantId must be at most 15 characters, got: " + value.length());
        if (value.length() < MIN_LENGTH)
            throw new IllegalArgumentException(
                "MerchantId must be at least " + MIN_LENGTH + " characters, got: " + value.length());
        if (!value.matches("[A-Za-z0-9]+"))
            throw new IllegalArgumentException(
                "MerchantId must be alphanumeric only: " + value);
    }

    public static MerchantId of(String value) {
        return new MerchantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
