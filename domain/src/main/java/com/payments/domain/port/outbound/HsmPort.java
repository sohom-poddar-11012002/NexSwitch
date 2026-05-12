package com.payments.domain.port.outbound;

import com.payments.domain.model.vo.PanHash;

public interface HsmPort {
    boolean verifyMac(byte[] messageBytes, byte[] mac);
    boolean verifyArqc(PanHash panHash, byte[] arqc, int atc, byte[] transactionData);
    byte[] translatePinBlock(byte[] pinBlock, String sourceKeyHandle, String destKeyHandle);
    byte[] generateArpc(byte[] arqc, String authResponseCode);
}
