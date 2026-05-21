package com.nexswitch.domain.model.vo;

import java.util.Objects;

// LEARN: EMV TLV — ISO 8583 Field 55 carries BER-TLV encoded chip data. Tag 9F26 (ARQC) is the
//        8-byte cryptogram proving the physical chip was present. Tag 9F36 (ATC) is a monotonic
//        counter — each transaction increments it, making every card session key mathematically unique.
public record EmvData(byte[] arqc, int atc, byte[] transactionData) {
    public EmvData {
        Objects.requireNonNull(arqc, "arqc");
        Objects.requireNonNull(transactionData, "transactionData");
        if (arqc.length != 8)
            throw new IllegalArgumentException("ARQC must be 8 bytes (Tag 9F26), got " + arqc.length);
        if (atc < 0 || atc > 0xFFFF)
            throw new IllegalArgumentException("ATC must be 0–65535 (Tag 9F36), got " + atc);
    }
}
