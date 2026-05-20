package com.nexswitch.adapters.outbound.hsm.pkcs11;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;

// LEARN: SunPKCS11 is a JDK-bundled bridge between JCA (Java Cryptography Architecture) and any
//        PKCS#11-compliant token (SoftHSM2 locally, Thales Luna / Safenet in production). The
//        critical security property: symmetric key bytes stay inside the HSM; only cipher results
//        cross the boundary. Java holds an opaque SecretKey handle — getEncoded() returns null for
//        non-extractable keys on real hardware, preventing key material from ever reaching the JVM.
public class SoftHsm2Session implements AutoCloseable {

    private final Provider  pkcs11Provider;
    private final KeyStore  keyStore;
    private final String    providerName;

    /**
     * Opens a PKCS#11 session against SoftHSM2.
     *
     * @param libraryPath  absolute path to libsofthsm2.so (Linux) or libsofthsm2.dylib (macOS)
     * @param slot         HSM slot index (0 = first initialized slot)
     * @param pin          user PIN for the token
     */
    public SoftHsm2Session(String libraryPath, int slot, char[] pin) throws Exception {
        // LEARN: SunPKCS11 inline config (-- prefix). JDK 9+ dropped the old config-file approach.
        //        Each session needs a unique provider name if multiple slots are open simultaneously.
        String config = String.format("--\nname=SoftHSM2-%d\nlibrary=%s\nslot=%d\n",
                slot, libraryPath, slot);

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException(
                "SunPKCS11 provider not found — ensure JDK includes jdk.crypto.cryptoki module");
        }
        pkcs11Provider = base.configure(config);
        Security.addProvider(pkcs11Provider);
        providerName = pkcs11Provider.getName();

        keyStore = KeyStore.getInstance("PKCS11", pkcs11Provider);
        keyStore.load(null, pin);
    }

    /**
     * Decrypts an 8-byte 3DES-ECB ciphertext using the named key from the HSM.
     * The actual decryption runs inside the PKCS#11 library — key material stays in the token.
     */
    public byte[] decrypt3Des(String keyAlias, byte[] ciphertext) throws Exception {
        SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", pkcs11Provider);
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Encrypts an 8-byte block using 3DES-ECB with the named key from the HSM.
     */
    public byte[] encrypt3Des(String keyAlias, byte[] plaintext) throws Exception {
        SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", pkcs11Provider);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(plaintext);
    }

    /**
     * Returns the raw key bytes for a named key in the token.
     *
     * NOTE: On SoftHSM2, keys can be marked extractable during import (as we do for dev/test).
     * On real hardware HSMs (Thales Luna, Safenet), getEncoded() returns null for non-extractable
     * keys — DUKPT derivation must then use vendor-specific HSM commands (e.g. Thales A6 command).
     * This extraction is used only for DUKPT derivation which has no native PKCS#11 mechanism.
     */
    public byte[] extractKey(String alias) throws Exception {
        SecretKey key = (SecretKey) keyStore.getKey(alias, null);
        if (key == null) {
            throw new IllegalStateException("Key not found in HSM: " + alias);
        }
        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            throw new IllegalStateException(
                "Key '" + alias + "' is not extractable — use a hardware-HSM DUKPT command instead");
        }
        return encoded;
    }

    public boolean containsKey(String alias) throws Exception {
        return keyStore.containsAlias(alias);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public Provider getProvider() {
        return pkcs11Provider;
    }

    @Override
    public void close() {
        Security.removeProvider(providerName);
    }
}
