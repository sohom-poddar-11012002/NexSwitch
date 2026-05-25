package com.nexswitch.domain.model.vo;

public record TxnRef(String value) {

    public TxnRef {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TxnRef must not be null or blank");
    }

    public static TxnRef of(String value) { return new TxnRef(value); }

    @Override
    public String toString() { return value; }
}
