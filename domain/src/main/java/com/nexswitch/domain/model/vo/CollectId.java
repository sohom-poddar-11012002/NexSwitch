package com.nexswitch.domain.model.vo;

// LEARN: WrapperType — distinct type prevents passing TxnRef or NpciTxnId where CollectId expected
public record CollectId(String value) {

    public CollectId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CollectId must not be null or blank");
    }

    public static CollectId of(String value) { return new CollectId(value); }

    @Override
    public String toString() { return value; }
}
