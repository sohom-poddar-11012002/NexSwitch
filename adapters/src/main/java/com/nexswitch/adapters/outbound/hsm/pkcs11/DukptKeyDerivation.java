package com.nexswitch.adapters.outbound.hsm.pkcs11;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

// LEARN: DUKPT (Derived Unique Key Per Transaction) ANSI X9.24-2004 — each terminal transaction
//        uses a mathematically unique 3DES key. Even if an attacker extracts one transaction key,
//        past and future keys remain safe (forward secrecy via non-reversible derivation). The
//        terminal KSN counter is monotonic — once a key is used it cannot be re-derived.
public final class DukptKeyDerivation {

    // BDK_VARIANT: XOR mask applied to BDK before deriving the right half of IPEK.
    // Using two variants (left = BDK, right = BDK ^ VARIANT) ensures IPEK_L and IPEK_R are
    // cryptographically independent — a compromise of one half doesn't expose the other.
    public static final byte[] BDK_VARIANT = {
        (byte)0xC0,(byte)0xC0,(byte)0xC0,(byte)0xC0, 0,0,0,0,
        (byte)0xC0,(byte)0xC0,(byte)0xC0,(byte)0xC0, 0,0,0,0
    };

    // LEARN: Key variant — applying a fixed XOR mask to the transaction key before using it for
    //        PIN encryption ensures the same key bits are never used for two purposes (PIN vs MAC
    //        vs DATA). Even an attacker who knows the PIN key cannot derive the MAC key.
    private static final byte[] PIN_VARIANT = {
        0,0,0,0,0,0,0,(byte)0xFF, 0,0,0,0,0,0,0,(byte)0xFF
    };

    private DukptKeyDerivation() {}

    /**
     * Derives the IPEK (Initial PIN Encryption Key) from the BDK and 10-byte KSN.
     * The KSN's 21-bit encryption counter is zeroed before derivation — IPEK is the
     * "root" key for a terminal session; per-transaction keys are derived from it.
     *
     * @param bdk    16-byte Base Derivation Key (double-length 3DES key stored in HSM)
     * @param ksn10  10-byte Key Serial Number (ISO 8583 Field 53 format)
     */
    public static byte[] deriveIpek(byte[] bdk, byte[] ksn10) throws GeneralSecurityException {
        byte[] ksnBase = ksnBase(ksn10);

        byte[] ipek = new byte[16];
        byte[] left  = tripleDesEncrypt(bdk, ksnBase);
        byte[] right = tripleDesEncrypt(xor(bdk, BDK_VARIANT), ksnBase);
        System.arraycopy(left,  0, ipek, 0, 8);
        System.arraycopy(right, 0, ipek, 8, 8);
        return ipek;
    }

    /**
     * Derives the per-transaction encryption key by applying the ANSI X9.24-2004
     * non-reversible key generation for each set bit in the 21-bit encryption counter.
     * Starting from IPEK, each iteration produces a new 16-byte key that cannot be
     * reversed to recover the IPEK or any previous transaction key.
     *
     * @param ipek   16-byte IPEK derived from BDK + KSN base
     * @param ksn10  10-byte KSN with the live encryption counter
     */
    public static byte[] deriveTransactionKey(byte[] ipek, byte[] ksn10) throws GeneralSecurityException {
        // LEARN: The 21-bit counter occupies the rightmost 21 bits of the 80-bit KSN.
        //        Byte 7: bits 4-0 = counter bits 20-16. Bytes 8-9: counter bits 15-0.
        int counter = ((ksn10[7] & 0x1F) << 16) | ((ksn10[8] & 0xFF) << 8) | (ksn10[9] & 0xFF);
        byte[] curKsn = ksnBase(ksn10);
        byte[] curKey = Arrays.copyOf(ipek, 16);

        for (int i = 20; i >= 0; i--) {
            if ((counter & (1 << i)) != 0) {
                // Map counter bit i into the 8-byte KSN register. The 21-bit counter
                // occupies bytes 5-7 (rightmost 3 bytes), bit i of counter → byte (7 - i/8), bit (i % 8).
                curKsn[7 - (i / 8)] |= (byte)(1 << (i % 8));
                curKey = nonReversibleKeyGeneration(curKey, curKsn);
            }
        }
        return curKey;
    }

    /**
     * Applies the PIN encryption key variant to a transaction key.
     * The resulting key decrypts ISO 9564 Format 0 PIN blocks from the terminal.
     */
    public static byte[] pinEncryptionKey(byte[] transactionKey) {
        return xor(transactionKey, PIN_VARIANT);
    }

    // ─── ANSI X9.24-2004 Non-Reversible Key Generation ───────────────────────

    // LEARN: Non-Reversible Key Generation — given current key K and masked KSN, produces
    //        a new key. The operation is one-way: K_new cannot be used to recover K.
    //        This is the security property that makes DUKPT forward-secure.
    public static byte[] nonReversibleKeyGeneration(byte[] key16, byte[] maskedKsn8)
            throws GeneralSecurityException {
        byte[] keyL = Arrays.copyOfRange(key16, 0, 8);
        byte[] keyR = Arrays.copyOfRange(key16, 8, 16);

        byte[] crypto = xor(keyR, maskedKsn8);
        byte[] newR   = desEncrypt(keyL, crypto);
        byte[] newL   = desEncrypt(xor(keyL, keyR), crypto);

        byte[] result = new byte[16];
        System.arraycopy(newL, 0, result, 0, 8);
        System.arraycopy(newR, 0, result, 8, 8);
        return result;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static byte[] ksnBase(byte[] ksn10) {
        byte[] base = Arrays.copyOf(ksn10, 8);
        base[7] &= (byte)0xE0;          // zero the 5 counter bits in byte 7 (bits 4-0)
        return base;
    }

    // 2-key 3DES (K1=key[0..7], K2=key[8..15], K3=key[0..7]) per ANSI X9.24
    public static byte[] tripleDesEncrypt(byte[] key16, byte[] data8) throws GeneralSecurityException {
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        SecretKey k = new SecretKeySpec(key24, "DESede");
        Cipher c    = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return c.doFinal(data8);
    }

    public static byte[] tripleDesDecrypt(byte[] key16, byte[] data8) throws GeneralSecurityException {
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        SecretKey k = new SecretKeySpec(key24, "DESede");
        Cipher c    = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k);
        return c.doFinal(data8);
    }

    // Single DES — used in the non-reversible key generation step
    public static byte[] desEncrypt(byte[] key8, byte[] data8) throws GeneralSecurityException {
        SecretKey k = new SecretKeySpec(key8, "DES");
        Cipher c    = Cipher.getInstance("DES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return c.doFinal(data8);
    }

    public static byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte)(a[i] ^ b[i % b.length]);
        }
        return result;
    }
}
