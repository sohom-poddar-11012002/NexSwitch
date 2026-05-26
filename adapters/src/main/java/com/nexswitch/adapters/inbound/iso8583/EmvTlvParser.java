package com.nexswitch.adapters.inbound.iso8583;

import com.nexswitch.domain.model.vo.EmvData;

import java.util.HashMap;
import java.util.Map;

// LEARN: BER-TLV (Basic Encoding Rules Tag-Length-Value) — ISO 7816-4 encoding used by EMV.
//        Tags are 1–2 bytes; a first byte low-nibble of 0x1F signals a two-byte tag.
//        Lengths are 1–3 bytes; 0x81 means next byte is length, 0x82 means next two bytes are length.
//        Field 55 of ISO 8583 carries the full EMV TLV blob from the chip's application cryptogram response.
final class EmvTlvParser {

    // Standard CDOL1 (Card Risk Management Data Object List 1) tags in MAC order.
    // These are the data elements the chip concatenates and MACs to produce the ARQC.
    // Visa/Mastercard CDOL1 = 9F02(6) | 9F03(6) | 9F1A(2) | 95(5) | 5F2A(2) | 9A(3) | 9C(1) | 9F37(4) | 82(2) | 9F36(2)
    private static final int[] CDOL1_TAGS = {0x9F02, 0x9F03, 0x9F1A, 0x95, 0x5F2A, 0x9A, 0x9C, 0x9F37, 0x82, 0x9F36};
    private static final int[] CDOL1_LENS = {6,      6,      2,      5,    2,      3,    1,    4,     2,    2};

    private EmvTlvParser() {}

    /**
     * Parses ISO 8583 Field 55 (EMV data) and extracts ARQC, ATC, and CDOL1 transaction data.
     * Returns null if Field 55 is absent, malformed, or missing the mandatory ARQC/ATC tags.
     */
    static EmvData parse(byte[] field55) {
        if (field55 == null || field55.length == 0) return null;

        Map<Integer, byte[]> tags = parseTlv(field55);

        byte[] arqc     = tags.get(0x9F26);   // Application Cryptogram (ARQC) — 8 bytes
        byte[] atcBytes = tags.get(0x9F36);   // Application Transaction Counter — 2 bytes

        if (arqc == null || arqc.length != 8) return null;
        if (atcBytes == null || atcBytes.length < 2) return null;

        int atc = ((atcBytes[0] & 0xFF) << 8) | (atcBytes[1] & 0xFF);
        byte[] transactionData = buildCdol1Data(tags);

        return new EmvData(arqc, atc, transactionData);
    }

    // Concatenate CDOL1 tag values in spec order; absent tags contribute zero bytes of their declared length.
    private static byte[] buildCdol1Data(Map<Integer, byte[]> tags) {
        byte[] out = new byte[33];  // sum of CDOL1_LENS = 33 bytes
        int pos = 0;
        for (int i = 0; i < CDOL1_TAGS.length; i++) {
            byte[] val     = tags.getOrDefault(CDOL1_TAGS[i], new byte[CDOL1_LENS[i]]);
            int    copyLen = Math.min(val.length, CDOL1_LENS[i]);
            System.arraycopy(val, 0, out, pos, copyLen);
            pos += CDOL1_LENS[i];
        }
        return out;
    }

    private static Map<Integer, byte[]> parseTlv(byte[] data) {
        Map<Integer, byte[]> result = new HashMap<>(64);
        int i = 0;
        while (i < data.length) {
            // Parse tag (1 or 2 bytes)
            int tag = data[i++] & 0xFF;
            if ((tag & 0x1F) == 0x1F) {
                if (i >= data.length) break;
                tag = (tag << 8) | (data[i++] & 0xFF);
            }

            // Parse length (1, 2, or 3 bytes)
            if (i >= data.length) break;
            int len = data[i++] & 0xFF;
            if (len == 0x81) {
                if (i >= data.length) break;
                len = data[i++] & 0xFF;
            } else if (len == 0x82) {
                if (i + 1 >= data.length) break;
                len = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
                i += 2;
            }

            // Parse value
            if (i + len > data.length) break;
            byte[] value = new byte[len];
            System.arraycopy(data, i, value, 0, len);
            result.put(tag, value);
            i += len;
        }
        return result;
    }
}
