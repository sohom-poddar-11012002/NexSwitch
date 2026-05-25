package com.nexswitch.domain.model.vo;

public record NpciTxnId(String value) {

    public NpciTxnId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("NpciTxnId must not be null or blank");
    }

    public static NpciTxnId of(String value) { return new NpciTxnId(value); }

    @Override
    public String toString() { return value; }
}
