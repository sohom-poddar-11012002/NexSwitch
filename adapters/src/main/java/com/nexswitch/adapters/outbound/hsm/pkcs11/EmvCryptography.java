package com.nexswitch.adapters.outbound.hsm.pkcs11;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

// LEARN: EMV Option A card session key derivation (EMVCo Book 2 §A1.4.1).
//        CSK = f(IMK, UDD) where UDD = ATC || 0x00*6. Each ATC value produces a distinct
//        CSK — even if an attacker recovers one CSK they cannot derive any other.
//        ARQC = 3DES-CBC-MAC(CSK, CDOL1_data) — proves physical chip presence.
//        ARPC = 3DES(CSK, ARQC XOR ARC_padded) — proves issuer authenticity to the terminal.
public final class EmvCryptography {

    public static final String ARC_APPROVED = "00";
    public static final String ARC_DECLINED = "01";

    private EmvCryptography() {}

    /**
     * Derives the Card Session Key (CSK) using EMV Option A.
     * UDD (Unique Derivation Data) = ATC bytes || 6 zero bytes.
     *
     * @param imk   16-byte Issuer Master Key stored in HSM
     * @param atc   Application Transaction Counter (Tag 9F36) — unique per transaction
     */
    public static byte[] deriveCardSessionKey(byte[] imk, int atc) throws GeneralSecurityException {
        byte[] udd = new byte[8];
        udd[0] = (byte)(atc >> 8);
        udd[1] = (byte)(atc & 0xFF);
        // bytes 2–7 remain zero — standard single-length key UDD format (Option A)

        // LEARN: Same two-half derivation as IPEK in DUKPT: left half uses IMK directly,
        //        right half uses IMK XOR BDK_VARIANT. The two halves are cryptographically
        //        independent — recovering one half reveals nothing about the other.
        byte[] cskL = DukptKeyDerivation.tripleDesEncrypt(imk, udd);
        byte[] cskR = DukptKeyDerivation.tripleDesEncrypt(DukptKeyDerivation.xor(imk, DukptKeyDerivation.BDK_VARIANT), udd);
        byte[] csk  = new byte[16];
        System.arraycopy(cskL, 0, csk, 0, 8);
        System.arraycopy(cskR, 0, csk, 8, 8);
        return csk;
    }

    /**
     * Computes ARQC = 3DES-CBC-MAC(cardSessionKey, cdol1Data).
     * Used both to generate (card-side) and verify (issuer/HSM-side) the ARQC.
     */
    public static byte[] computeArqc(byte[] cardSessionKey, byte[] cdol1Data)
            throws GeneralSecurityException {
        return compute3DesMac(cardSessionKey, cdol1Data);
    }

    /**
     * Computes ARPC using EMV Method 1: ARPC = 3DES(CSK, ARQC XOR ARC_padded).
     * The terminal uses the ARPC to verify the issuer's 0110 response is authentic.
     *
     * @param cardSessionKey derived CSK for this transaction
     * @param arqc           8-byte ARQC from the chip (Tag 9F26)
     * @param authResponseCode  "00" approved / "01" declined (becomes the ARC bytes)
     */
    public static byte[] computeArpc(byte[] cardSessionKey, byte[] arqc, String authResponseCode)
            throws GeneralSecurityException {
        byte[] arc = authResponseCode.getBytes();
        byte[] pad = new byte[8];
        System.arraycopy(arc, 0, pad, 0, Math.min(arc.length, 8));
        byte[] input = DukptKeyDerivation.xor(Arrays.copyOf(arqc, 8), pad);
        return DukptKeyDerivation.tripleDesEncrypt(cardSessionKey, input);
    }

    // LEARN: 3DES-CBC-MAC — chain each 8-byte padded block through DES3 in CBC mode;
    //        the last block is the MAC. Any bit-flip in the data produces a completely
    //        different MAC (avalanche effect) — even a single changed byte is detectable.
    public static byte[] compute3DesMac(byte[] key16, byte[] data) throws GeneralSecurityException {
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        SecretKey key = new SecretKeySpec(key24, "DESede");

        int padLen = data.length % 8 == 0 ? 0 : 8 - (data.length % 8);
        byte[] padded = Arrays.copyOf(data, data.length + padLen);

        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] block = new byte[8];
        for (int i = 0; i < padded.length; i += 8) {
            for (int j = 0; j < 8; j++) block[j] = (byte)(block[j] ^ padded[i + j]);
            block = cipher.doFinal(block);
        }
        return block;
    }
}
