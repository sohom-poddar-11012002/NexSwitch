package com.nexswitch.test.fixture;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pre-built ISO 8583 field maps for integration tests.
 * Returns Map<Integer, String> — field number → field value.
 * The inbound adapter (built in #15) constructs ISOMsg from these maps.
 *
 * Field values match the demo card: PAN 4539148803436467, Amount ₹6,000.
 */
// LEARN: ISO8583 — Map<Integer,String> models variable bitmap; field presence = key in map, absence = key missing
public final class Iso8583MessageFixture {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter DATE_ONLY  = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter TIME_ONLY  = DateTimeFormatter.ofPattern("HHmmss");

    private Iso8583MessageFixture() {}

    /**
     * MTI 0100 — Authorization Request.
     * Field 22 = 051 (chip + PIN, contact EMV).
     */
    public static Map<Integer, String> valid0100() {
        Map<Integer, String> fields = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        fields.put(2,  "4539148803436467");         // PAN
        fields.put(3,  "000000");                    // Processing code — purchase
        fields.put(4,  "000000600000");              // Amount — ₹6,000.00 (paise, 12 digits)
        fields.put(7,  now.format(DATE_TIME));       // Transmission date/time
        fields.put(11, "000001");                    // STAN
        fields.put(12, now.format(TIME_ONLY));       // Local transaction time
        fields.put(13, now.format(DATE_ONLY));       // Local transaction date
        fields.put(18, "5411");                      // MCC
        fields.put(22, "051");                       // POS entry mode — chip + PIN
        fields.put(25, "00");                        // POS condition code — normal
        fields.put(35, "4539148803436467=2812");     // Track 2 equivalent data
        fields.put(37, "000000000001");              // Retrieval reference number
        fields.put(41, "TERM0042");                  // Terminal ID (8 chars)
        fields.put(42, "MERCH0000999  ");            // Merchant ID (15 chars, padded)
        fields.put(43, "Test Merchant Pvt Ltd  IN"); // Merchant name/location
        fields.put(49, "356");                       // Currency code — INR
        return fields;
    }

    /**
     * MTI 0100 — Authorization Request for a ₹500 QR / UPI collect payment.
     * No PAN (Field 2), no Track 2 (Field 35) — VPA used instead.
     */
    public static Map<Integer, String> valid0100Upi() {
        Map<Integer, String> fields = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        fields.put(3,  "000000");
        fields.put(4,  "000000050000");              // ₹500.00
        fields.put(7,  now.format(DATE_TIME));
        fields.put(11, "000002");
        fields.put(12, now.format(TIME_ONLY));
        fields.put(13, now.format(DATE_ONLY));
        fields.put(18, "5411");
        fields.put(22, "010");                       // POS entry mode — manual / QR
        fields.put(41, "TERM0042");
        fields.put(42, "MERCH0000999  ");
        fields.put(49, "356");
        fields.put(102, "merchant@payswiff");        // VPA (Field 102, LLVAR)
        return fields;
    }

    /**
     * MTI 0400 — Reversal Request.
     * Built from the original 0100 — same STAN, same amount, same terminal.
     */
    public static Map<Integer, String> valid0400(String originalStan) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        fields.put(2,  "4539148803436467");
        fields.put(3,  "000000");
        fields.put(4,  "000000600000");
        fields.put(7,  now.format(DATE_TIME));
        fields.put(11, originalStan);                // STAN — must match original 0100
        fields.put(12, now.format(TIME_ONLY));
        fields.put(13, now.format(DATE_ONLY));
        fields.put(18, "5411");
        fields.put(22, "051");
        fields.put(37, "000000000001");
        fields.put(41, "TERM0042");
        fields.put(42, "MERCH0000999  ");
        fields.put(49, "356");
        fields.put(90, "010000000100000000000000");  // Original data elements (Field 90)
        return fields;
    }

    /**
     * MTI 0100 — triggers a decline (response code 05).
     * Amount ₹99,999 — above merchant per-transaction limit.
     */
    public static Map<Integer, String> overlimitAmount() {
        Map<Integer, String> fields = new LinkedHashMap<>(valid0100());
        fields.put(4, "000009999900");               // ₹99,999.00
        fields.put(11, "000003");
        return fields;
    }
}
