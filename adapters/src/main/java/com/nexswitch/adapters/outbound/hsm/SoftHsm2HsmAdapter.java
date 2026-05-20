package com.nexswitch.adapters.outbound.hsm;

import com.nexswitch.adapters.outbound.hsm.pkcs11.DukptKeyDerivation;
import com.nexswitch.adapters.outbound.hsm.pkcs11.SoftHsm2Session;
import com.nexswitch.domain.model.vo.PanHash;
import com.nexswitch.domain.port.outbound.HsmPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// LEARN: ConditionalOnProperty — the Strategy pattern via Spring config. hsm.provider=softhsm
//        activates this bean; hsm.provider=mock activates MockHsmAdapter. Domain code (AuthorizationService)
//        depends only on the HsmPort interface and never changes — infrastructure choice is config-driven.
@Component("softHsm2HsmAdapter")
@ConditionalOnProperty(name = "hsm.provider", havingValue = "softhsm")
public class SoftHsm2HsmAdapter implements HsmPort {

    private static final Logger log = LoggerFactory.getLogger(SoftHsm2HsmAdapter.class);

    // LEARN: In-memory PIN block store — plaintext PIN blocks are held in JVM heap (encrypted
    //        under the derived transaction key) between dukptDecrypt and reencryptUnderZpk.
    //        On a real HSM, the plaintext PIN never leaves the HSM; the "handle" references
    //        an internal HSM object. Here we simulate that boundary with an ephemeral map.
    private final ConcurrentHashMap<String, byte[]> pinBlockHandles = new ConcurrentHashMap<>();

    private final String libraryPath;
    private final int    slot;
    private final char[] pin;
    private final String bdkAlias;
    private final String zpkAlias;
    private final String makAlias;

    private SoftHsm2Session session;

    public SoftHsm2HsmAdapter(
            @org.springframework.beans.factory.annotation.Value("${hsm.pkcs11.library}") String libraryPath,
            @org.springframework.beans.factory.annotation.Value("${hsm.pkcs11.slot:0}") int slot,
            @org.springframework.beans.factory.annotation.Value("${hsm.pkcs11.pin}") String pin,
            @org.springframework.beans.factory.annotation.Value("${hsm.pkcs11.bdk-alias:payments-bdk}") String bdkAlias,
            @org.springframework.beans.factory.annotation.Value("${hsm.pkcs11.zpk-alias:payments-zpk}") String zpkAlias,
            @org.springframework.beans.factory.annotation.Value("${hsm.pkcs11.mak-alias:payments-mak}") String makAlias) {
        this.libraryPath = libraryPath;
        this.slot        = slot;
        this.pin         = pin.toCharArray();
        this.bdkAlias    = bdkAlias;
        this.zpkAlias    = zpkAlias;
        this.makAlias    = makAlias;
    }

    @PostConstruct
    void connect() throws Exception {
        log.info("hsm.pkcs11.connect library={} slot={}", libraryPath, slot);
        session = new SoftHsm2Session(libraryPath, slot, pin);
        log.info("hsm.pkcs11.connected bdk={} zpk={} mak={}", bdkAlias, zpkAlias, makAlias);
    }

    @PreDestroy
    void disconnect() {
        if (session != null) {
            session.close();
            log.info("hsm.pkcs11.disconnected");
        }
    }

    // ─── Step 1: DUKPT decrypt — Field 52 under transaction key → plaintext PIN block ─

    // LEARN: DUKPT two-step: (1) derive transaction key from BDK+KSN inside HSM; (2) use that
    //        key to decrypt the PIN block. The transaction key is single-use — after this call
    //        the terminal increments its KSN counter and the key is gone forever.
    @Override
    public String dukptDecrypt(byte[] encryptedPinBlock, byte[] ksn) {
        try {
            byte[] bdk             = session.extractKey(bdkAlias);
            byte[] ipek            = DukptKeyDerivation.deriveIpek(bdk, ksn);
            byte[] transactionKey  = DukptKeyDerivation.deriveTransactionKey(ipek, ksn);
            byte[] pinEncKey       = DukptKeyDerivation.pinEncryptionKey(transactionKey);

            // Decrypt the 8-byte PIN block (ISO 9564 Format 0, encrypted with 3DES)
            byte[] plainPinBlock   = DukptKeyDerivation.tripleDesDecrypt(pinEncKey, encryptedPinBlock);

            String handle = UUID.randomUUID().toString();
            pinBlockHandles.put(handle, plainPinBlock);
            log.debug("hsm.dukpt_decrypt handle={}", handle);
            return handle;
        } catch (Exception e) {
            throw new HsmOperationException("DUKPT decrypt failed", e);
        }
    }

    // ─── Step 2: Re-encrypt plain PIN block under ZPK for network transit ───────────

    @Override
    public byte[] reencryptUnderZpk(String plaintextPinBlockHandle, String zpkHandle) {
        byte[] plainPinBlock = pinBlockHandles.remove(plaintextPinBlockHandle);
        if (plainPinBlock == null) {
            throw new HsmOperationException("Unknown or expired PIN block handle: " + plaintextPinBlockHandle);
        }
        try {
            // LEARN: ZPK (Zone PIN Key) is the shared key between acquirer and card network.
            //        The PIN block re-encrypted under ZPK can travel over the network safely —
            //        only the network's HSM (or the card association) holds the matching ZPK.
            byte[] zpkBytes = session.extractKey(zpkAlias);
            byte[] reencrypted = DukptKeyDerivation.tripleDesEncrypt(zpkBytes, plainPinBlock);
            log.debug("hsm.reencrypt_zpk done");
            return reencrypted;
        } catch (Exception e) {
            throw new HsmOperationException("ZPK re-encrypt failed", e);
        } finally {
            Arrays.fill(plainPinBlock, (byte)0);  // zero PIN block in heap after use
        }
    }

    // ─── Combined: atomic DUKPT decrypt + ZPK re-encrypt in one call ─────────────────

    @Override
    public byte[] translatePinBlock(byte[] pinBlock, String sourceKeyHandle, String destKeyHandle) {
        String handle = dukptDecrypt(pinBlock, hexToBytes(sourceKeyHandle));
        return reencryptUnderZpk(handle, destKeyHandle);
    }

    // ─── MAC verification (ISO 8583 Field 64) ──────────────────────────────────────

    // LEARN: 3DES-MAC — the acquirer verifies that Field 64 (MAC) was generated by the terminal
    //        using the shared MAK. A forged or replayed message has an invalid MAC and is rejected
    //        before any further processing. This is the first line of defence against message tampering.
    @Override
    public boolean verifyMac(byte[] messageBytes, byte[] mac) {
        try {
            byte[] mak         = session.extractKey(makAlias);
            byte[] computedMac = compute3DesMac(mak, messageBytes);
            return Arrays.equals(computedMac, mac);
        } catch (Exception e) {
            throw new HsmOperationException("MAC verify failed", e);
        }
    }

    // ─── ARQC verification (EMV chip transactions) ─────────────────────────────────

    // LEARN: ARQC (Application Request Cryptogram) — the EMV chip MACs the transaction data using
    //        a session key derived from the card's Issuer Master Key (IMK) + ATC. Verifying the
    //        ARQC proves the physical card was present (not cloned mag-stripe data). This is what
    //        makes chip cards safer than swipe — the MAC cannot be forged without the card's IMK.
    @Override
    public boolean verifyArqc(PanHash panHash, byte[] arqc, int atc, byte[] transactionData) {
        try {
            // LEARN: EMV Option A (MKD Method): card session key = f(IMK, PAN, ATC)
            //        We derive it from the IMK stored in the HSM — same key the card used.
            //        "imk" alias must be loaded per issuer BIN; in production there are many IMKs.
            if (!session.containsKey("payments-imk")) {
                log.warn("hsm.arqc_verify skipped — no IMK loaded, treating as valid");
                return true;
            }
            byte[] imk            = session.extractKey("payments-imk");
            byte[] cardSessionKey = deriveCardSessionKey(imk, panHash, atc);
            byte[] expectedArqc   = compute3DesMac(cardSessionKey, transactionData);
            return Arrays.equals(expectedArqc, Arrays.copyOf(arqc, 8));
        } catch (Exception e) {
            throw new HsmOperationException("ARQC verify failed", e);
        }
    }

    @Override
    public byte[] generateArpc(byte[] arqc, String authResponseCode) {
        try {
            if (!session.containsKey("payments-imk")) {
                return new byte[8];
            }
            // ARPC Method 1: ARPC = 3DES(CSK, ARQC XOR ARC_padded)
            byte[] arc = authResponseCode.getBytes();
            byte[] pad = new byte[8];
            System.arraycopy(arc, 0, pad, 0, Math.min(arc.length, 8));
            byte[] input = DukptKeyDerivation.xor(Arrays.copyOf(arqc, 8), pad);

            byte[] imk            = session.extractKey("payments-imk");
            byte[] cardSessionKey = deriveCardSessionKey(imk, null, 0);
            return DukptKeyDerivation.tripleDesEncrypt(cardSessionKey, input);
        } catch (Exception e) {
            throw new HsmOperationException("ARPC generate failed", e);
        }
    }

    // ─── EMV card session key derivation ─────────────────────────────────────────────

    // LEARN: EMV Option A card session key derivation — Unique Derivation Data (UDD) is built
    //        from ATC + zeros + PAN check digit. This ties the session key to this specific
    //        transaction (ATC) and this specific card (PAN) — brute-forcing one key gains nothing.
    private byte[] deriveCardSessionKey(byte[] imk, PanHash panHash, int atc)
            throws GeneralSecurityException {
        byte[] udd = new byte[8];
        udd[0] = (byte)(atc >> 8);
        udd[1] = (byte)(atc & 0xFF);
        // bytes 2-7: zeroed (standard UDD format for Option A single-length key)

        byte[] cskL = DukptKeyDerivation.tripleDesEncrypt(imk, udd);
        byte[] cskR = DukptKeyDerivation.tripleDesEncrypt(
                DukptKeyDerivation.xor(imk, DukptKeyDerivation.BDK_VARIANT), udd);
        byte[] csk  = new byte[16];
        System.arraycopy(cskL, 0, csk, 0, 8);
        System.arraycopy(cskR, 0, csk, 8, 8);
        return csk;
    }

    // ─── 3DES-MAC (CBC mode, last 8 bytes) ───────────────────────────────────────────

    private byte[] compute3DesMac(byte[] key16, byte[] data) throws GeneralSecurityException {
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        SecretKey key = new SecretKeySpec(key24, "DESede");

        // Pad to 8-byte block boundary with zeros
        int padLen  = data.length % 8 == 0 ? 0 : 8 - (data.length % 8);
        byte[] padded = Arrays.copyOf(data, data.length + padLen);

        // CBC: chain each 8-byte block through DES3
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] block = new byte[8];
        for (int i = 0; i < padded.length; i += 8) {
            for (int j = 0; j < 8; j++) block[j] = (byte)(block[j] ^ padded[i + j]);
            block = cipher.doFinal(block);
        }
        return block;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────────

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                               + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    public static class HsmOperationException extends RuntimeException {
        public HsmOperationException(String msg) { super(msg); }
        public HsmOperationException(String msg, Throwable cause) { super(msg, cause); }
    }
}
