package com.nexswitch.domain.model.vo;

// LEARN: NetworkAssigned — 6-char approval code from issuer; absent until AUTHORIZED state
public record AuthorizationCode(String value) {

    private static final int REQUIRED_LENGTH = 6;

    public AuthorizationCode {
        if (value == null)
            throw new IllegalArgumentException("AuthorizationCode must not be null");
        value = value.toUpperCase();
        if (value.length() != REQUIRED_LENGTH)
            throw new IllegalArgumentException(
                "AuthorizationCode must be exactly 6 characters, got: " + value.length());
        if (!value.matches("[A-Z0-9]{6}"))
            throw new IllegalArgumentException(
                "AuthorizationCode must be alphanumeric only: " + value);
    }

    public static AuthorizationCode of(String value) {
        return new AuthorizationCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
