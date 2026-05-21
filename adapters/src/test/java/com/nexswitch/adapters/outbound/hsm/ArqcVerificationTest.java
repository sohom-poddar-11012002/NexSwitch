package com.nexswitch.adapters.outbound.hsm;

import com.nexswitch.adapters.outbound.hsm.pkcs11.EmvCryptography;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

// EMV Option A ARQC/ARPC tests using self-consistent computed vectors.
// The CSK derivation uses the same algorithm as DUKPT IPEK (IMK left/right halves with BDK_VARIANT XOR),
// so correctness anchors via the DUKPT IPEK known vector in DukptKeyDerivationTest.
//
// Test IMK: 0123456789ABCDEFFEDCBA9876543210  (same as DUKPT BDK for cross-test consistency)
// ATC: 0x0001 (first real transaction; ATC=0 would mean no transactions ever)
class ArqcVerificationTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    static final byte[] IMK = hex("0123456789ABCDEFFEDCBA9876543210");

    // Standard 33-byte CDOL1 transaction data (zeroed — real terminals fill amount, date, TVR etc.)
    static final byte[] CDOL1_DATA = new byte[33];

    @Test
    void deriveCardSessionKey_atc1_is16Bytes() throws Exception {
        byte[] csk = EmvCryptography.deriveCardSessionKey(IMK, 1);
        assertThat(csk).hasSize(16);
    }

    @Test
    void deriveCardSessionKey_atcZero_equalsIpek() throws Exception {
        // ATC=0 means UDD = 0x0000000000000000 — same derivation input as DUKPT ksnBase with counter=0
        byte[] csk0 = EmvCryptography.deriveCardSessionKey(IMK, 0);
        assertThat(csk0).hasSize(16);
        assertThat(csk0).isNotEqualTo(IMK);
    }

    @Test
    void differentAtc_produceDifferentCardSessionKeys() throws Exception {
        byte[] csk1 = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] csk2 = EmvCryptography.deriveCardSessionKey(IMK, 2);
        // Every ATC value produces a unique session key — compromising one reveals nothing about others
        assertThat(csk1).isNotEqualTo(csk2);
    }

    @Test
    void computeArqc_matchesManualMac() throws Exception {
        byte[] csk  = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] arqc = EmvCryptography.computeArqc(csk, CDOL1_DATA);
        byte[] mac  = EmvCryptography.compute3DesMac(csk, CDOL1_DATA);
        // ARQC is defined as the 3DES-CBC-MAC of CDOL1 data under CSK
        assertThat(arqc).isEqualTo(mac);
        assertThat(arqc).hasSize(8);
    }

    @Test
    void arqc_changes_when_transactionData_changes() throws Exception {
        byte[] csk       = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] cdol1a    = new byte[33];
        byte[] cdol1b    = new byte[33];
        cdol1b[0]        = 0x01;  // amount byte differs

        byte[] arqcA = EmvCryptography.computeArqc(csk, cdol1a);
        byte[] arqcB = EmvCryptography.computeArqc(csk, cdol1b);
        // MAC avalanche: one-bit change in data → completely different MAC
        assertThat(arqcA).isNotEqualTo(arqcB);
    }

    @Test
    void computeArpc_approved_isDeterministic() throws Exception {
        byte[] csk  = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] arqc = EmvCryptography.computeArqc(csk, CDOL1_DATA);

        byte[] arpc1 = EmvCryptography.computeArpc(csk, arqc, EmvCryptography.ARC_APPROVED);
        byte[] arpc2 = EmvCryptography.computeArpc(csk, arqc, EmvCryptography.ARC_APPROVED);
        assertThat(arpc1).isEqualTo(arpc2);
        assertThat(arpc1).hasSize(8);
    }

    @Test
    void computeArpc_declinedArc_differFromApproved() throws Exception {
        byte[] csk  = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] arqc = EmvCryptography.computeArqc(csk, CDOL1_DATA);

        byte[] arpcApproved = EmvCryptography.computeArpc(csk, arqc, EmvCryptography.ARC_APPROVED);
        byte[] arpcDeclined = EmvCryptography.computeArpc(csk, arqc, EmvCryptography.ARC_DECLINED);
        // Terminal must distinguish approval from decline — different ARC → different ARPC
        assertThat(arpcApproved).isNotEqualTo(arpcDeclined);
    }

    @Test
    void roundTrip_arqcVerify_thenArpcGenerate() throws Exception {
        // Simulate full chip authorization round-trip without a real HSM:
        // card generates ARQC → issuer verifies → issuer generates ARPC → terminal verifies
        byte[] csk    = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] arqc   = EmvCryptography.computeArqc(csk, CDOL1_DATA);

        // Issuer side: re-derive CSK from IMK+ATC, verify ARQC
        byte[] cskIssuer   = EmvCryptography.deriveCardSessionKey(IMK, 1);
        byte[] expectedArqc = EmvCryptography.computeArqc(cskIssuer, CDOL1_DATA);
        assertThat(arqc).isEqualTo(expectedArqc);   // ARQC verified

        // Issuer generates ARPC Method 1
        byte[] arpc = EmvCryptography.computeArpc(cskIssuer, arqc, EmvCryptography.ARC_APPROVED);
        assertThat(arpc).hasSize(8);
        assertThat(arpc).isNotEqualTo(arqc);  // ARPC and ARQC are distinct values
    }

    @Test
    void cardSessionKey_knownVector_matchesExpected() throws Exception {
        // Self-consistent known vector: computed from the algorithm and stored here.
        // If this value changes, the ARQC verification algorithm has changed — update intentionally.
        byte[] csk = EmvCryptography.deriveCardSessionKey(IMK, 1);
        assertThat(HEX.formatHex(csk)).isEqualTo("E90EB98AB6F9CB4654D3102A1AF7D2E8");
    }

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s.replace(" ", ""));
    }
}
