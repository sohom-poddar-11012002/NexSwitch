package com.payments.domain.model.vo;

public record SystemTraceAuditNumber(String value) {

    private static final int REQUIRED_LENGTH = 6;

    public SystemTraceAuditNumber {
        if (value == null)
            throw new IllegalArgumentException("SystemTraceAuditNumber must not be null");
        if (value.length() != REQUIRED_LENGTH)
            throw new IllegalArgumentException(
                "SystemTraceAuditNumber (STAN) must be exactly 6 digits, got: " + value.length());
        if (!value.matches("[0-9]{6}"))
            throw new IllegalArgumentException(
                "SystemTraceAuditNumber must be numeric only: " + value);
    }

    public static SystemTraceAuditNumber of(String value) {
        return new SystemTraceAuditNumber(value);
    }

    public int intValue() {
        return Integer.parseInt(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
