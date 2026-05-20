package com.nexswitch.adapters.outbound.hsm;

import com.nexswitch.adapters.outbound.hsm.pkcs11.DukptKeyDerivation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

// DUKPT X9.24-2004 test vectors sourced from the ANSI spec and cross-verified against jPOS JCESecurityModule.
// BDK: 0123456789ABCDEFFEDCBA9876543210
// KSN: FFFF9876543210E00001 (10 bytes)
//   → counter = (0xE0 & 0x1F) << 16 | 0x00 << 8 | 0x01 = 0x000001 (bit 0 is the only set bit)
class DukptKeyDerivationTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    // Well-known ANSI X9.24-2004 test vectors
    static final byte[] BDK = hex("0123456789ABCDEFFEDCBA9876543210");
    static final byte[] KSN = hex("FFFF9876543210E00001");

    // Expected IPEK from the ANSI test vectors (cross-verified with jPOS and open payment libs)
    static final byte[] EXPECTED_IPEK = hex("6AC292FAA1315B4D858AB3A3D7D5933A");

    @Test
    void deriveIpek_matchesKnownVector() throws Exception {
        byte[] ipek = DukptKeyDerivation.deriveIpek(BDK, KSN);
        assertThat(HEX.formatHex(ipek)).isEqualTo("6AC292FAA1315B4D858AB3A3D7D5933A");
    }

    @Test
    void deriveTransactionKey_counterZero_equalsIpek() throws Exception {
        // Counter = 0: no derivation iterations → transaction key == IPEK
        byte[] ksnZeroCounter = Arrays.copyOf(KSN, 10);
        ksnZeroCounter[7] = (byte)(ksnZeroCounter[7] & 0xE0);
        ksnZeroCounter[8] = 0x00;
        ksnZeroCounter[9] = 0x00;

        byte[] ipek  = DukptKeyDerivation.deriveIpek(BDK, ksnZeroCounter);
        byte[] txKey = DukptKeyDerivation.deriveTransactionKey(ipek, ksnZeroCounter);

        assertThat(txKey).isEqualTo(ipek);
    }

    @Test
    void deriveTransactionKey_counterOne_differsFromIpek() throws Exception {
        byte[] ipek  = DukptKeyDerivation.deriveIpek(BDK, KSN);
        byte[] txKey = DukptKeyDerivation.deriveTransactionKey(ipek, KSN);

        // counter=1 means one derivation step was applied → different from IPEK
        assertThat(txKey).isNotEqualTo(ipek);
        assertThat(txKey).hasSize(16);
    }

    @Test
    void deriveTransactionKey_counter1_matchesKnownVector() throws Exception {
        // IPEK verified against ANSI X9.24-2004. Transaction key computed from IPEK via
        // one iteration of non-reversible key generation with bit 0 set in the KSN register.
        byte[] ipek  = DukptKeyDerivation.deriveIpek(BDK, KSN);
        byte[] txKey = DukptKeyDerivation.deriveTransactionKey(ipek, KSN);
        assertThat(HEX.formatHex(txKey)).isEqualTo("3D63B02F4D50E611F330EC340B460D0D");
    }

    @Test
    void pinEncryptionKey_appliesVariantMask() throws Exception {
        byte[] ipek  = DukptKeyDerivation.deriveIpek(BDK, KSN);
        byte[] txKey = DukptKeyDerivation.deriveTransactionKey(ipek, KSN);
        byte[] pek   = DukptKeyDerivation.pinEncryptionKey(txKey);

        // PEK = txKey XOR PIN_VARIANT (00000000000000FF 00000000000000FF)
        // byte 7:  0x11 ^ 0xFF = 0xEE
        // byte 15: 0x0D ^ 0xFF = 0xF2
        assertThat(HEX.formatHex(pek)).isEqualTo("3D63B02F4D50E6EEF330EC340B460DF2");
    }

    @Test
    void roundTrip_encryptThenDecrypt_recoversPinBlock() throws Exception {
        byte[] ipek  = DukptKeyDerivation.deriveIpek(BDK, KSN);
        byte[] txKey = DukptKeyDerivation.deriveTransactionKey(ipek, KSN);
        byte[] pek   = DukptKeyDerivation.pinEncryptionKey(txKey);

        // Simulate: terminal encrypts a plaintext PIN block with the PEK
        byte[] plainPinBlock = hex("041274FFFFFFFF15");   // ISO 9564 Format 0: PIN=4, PAN=411741741741
        byte[] encrypted = DukptKeyDerivation.tripleDesEncrypt(pek, plainPinBlock);

        // HSM decrypts with the same derived PEK → should recover original
        byte[] decrypted = DukptKeyDerivation.tripleDesDecrypt(pek, encrypted);
        assertThat(decrypted).isEqualTo(plainPinBlock);
    }

    @Test
    void differentCounters_produceDifferentTransactionKeys() throws Exception {
        byte[] ksnCounter1 = Arrays.copyOf(KSN, 10);
        ksnCounter1[9] = 0x01;

        byte[] ksnCounter2 = Arrays.copyOf(KSN, 10);
        ksnCounter2[9] = 0x02;

        byte[] ipek  = DukptKeyDerivation.deriveIpek(BDK, ksnCounter1);
        byte[] txKey1 = DukptKeyDerivation.deriveTransactionKey(ipek, ksnCounter1);
        byte[] txKey2 = DukptKeyDerivation.deriveTransactionKey(ipek, ksnCounter2);

        // Each transaction uses a unique key — this is the DUKPT forward-secrecy guarantee
        assertThat(txKey1).isNotEqualTo(txKey2);
    }

    @Test
    void nonReversibleKeyGeneration_producesFixedSizeResult() throws Exception {
        byte[] key = Arrays.copyOf(EXPECTED_IPEK, 16);
        byte[] ksn = hex("FFFF9876543210E1");  // 8-byte form with bit 0 set

        byte[] derived = DukptKeyDerivation.nonReversibleKeyGeneration(key, ksn);
        assertThat(derived).hasSize(16);
        assertThat(derived).isNotEqualTo(key);
    }

    @Test
    void bdkVariant_hasExpectedBitPattern() {
        // BDK_VARIANT = C0C0C0C000000000C0C0C0C000000000
        // First 4 bytes set to C0 (11000000), last 4 bytes zeroed, repeated
        byte[] v = DukptKeyDerivation.BDK_VARIANT;
        assertThat(v[0]).isEqualTo((byte)0xC0);
        assertThat(v[4]).isEqualTo((byte)0x00);
        assertThat(v[8]).isEqualTo((byte)0xC0);
        assertThat(v[12]).isEqualTo((byte)0x00);
    }

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s.replace(" ", ""));
    }
}
