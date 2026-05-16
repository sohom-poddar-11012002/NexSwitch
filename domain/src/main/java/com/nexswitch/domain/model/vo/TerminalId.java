package com.nexswitch.domain.model.vo;

// LEARN: WrapperType — 8-char constraint matches ISO 8583 field 41 terminal ID format
public record TerminalId(String value) {

    private static final int REQUIRED_LENGTH = 8;

    public TerminalId {
        if (value == null)
            throw new IllegalArgumentException("TerminalId must not be null");
        if (value.length() != REQUIRED_LENGTH)
            throw new IllegalArgumentException(
                "TerminalId must be exactly 8 characters, got: " + value.length());
        if (!value.matches("[A-Za-z0-9]{8}"))
            throw new IllegalArgumentException(
                "TerminalId must be alphanumeric only: " + value);
    }

    public static TerminalId of(String value) {
        return new TerminalId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
