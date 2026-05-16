package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.vo.PanHash;

// LEARN: SecurityBoundary — all HSM operations defined here; domain knows operations, not key handles
public interface HsmPort {

    // Step 1 of the DUKPT two-step: derive terminal transaction key from BDK + KSN,
    // decrypt Field 52 (DUKPT-encrypted PIN block) → plaintext PIN block stays inside HSM.
    // Returns an opaque session handle referencing the decrypted PIN block in HSM memory.
    String dukptDecrypt(byte[] encryptedPinBlock, byte[] ksn);

    // Step 2 of the DUKPT two-step: re-encrypt the plaintext PIN block (referenced by handle)
    // under the ZPK for onward transit to the card network. PIN never exposed outside HSM.
    byte[] reencryptUnderZpk(String plaintextPinBlockHandle, String zpkHandle);

    // Convenience: executes both DUKPT steps atomically in one HSM session.
    // Equivalent to dukptDecrypt → reencryptUnderZpk. Preferred for single-hop PIN transit.
    byte[] translatePinBlock(byte[] pinBlock, String sourceKeyHandle, String destKeyHandle);

    boolean verifyMac(byte[] messageBytes, byte[] mac);

    boolean verifyArqc(PanHash panHash, byte[] arqc, int atc, byte[] transactionData);

    byte[] generateArpc(byte[] arqc, String authResponseCode);
}
