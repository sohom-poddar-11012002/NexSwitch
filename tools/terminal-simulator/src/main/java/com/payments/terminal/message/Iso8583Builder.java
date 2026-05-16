package com.payments.terminal.message;

import com.payments.terminal.config.TerminalConfig;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// LEARN: ISO8583Builder — jPOS GenericPackager reads field definitions from XML; bitmap bits set automatically from present fields
public final class Iso8583Builder {

    private static final String PACKAGER_CLASSPATH = "cfg/terminal-packager.xml";
    private static final DateTimeFormatter MMDD_HHMMSS = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter HHMMSS      = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter MMDD        = DateTimeFormatter.ofPattern("MMdd");

    private final GenericPackager packager;

    public Iso8583Builder() {
        // LEARN: ClassLoaderResource — getClassLoader().getResourceAsStream() resolves from classpath root;
        //        avoids GenericPackager(String) which resolves relative to CWD first, then classpath
        try (var in = Iso8583Builder.class.getClassLoader().getResourceAsStream(PACKAGER_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("terminal-packager.xml not found on classpath at: " + PACKAGER_CLASSPATH);
            }
            packager = new GenericPackager(in);
        } catch (ISOException e) {
            throw new IllegalStateException("Failed to parse ISO 8583 packager XML", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read ISO 8583 packager XML", e);
        }
    }

    /**
     * Builds an MTI 0100 Authorization Request using the demo Visa PAN.
     *
     * @param config     terminal identity (ID, merchant ID, etc.)
     * @param stan       6-digit zero-padded STAN from StanGenerator
     * @param amountPaise 12-digit zero-padded amount in paise (000000600000 = ₹6,000.00)
     */
    public ISOMsg buildAuthRequest(TerminalConfig config, String stan, String amountPaise) {
        LocalDateTime now = LocalDateTime.now();
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0100");
            msg.set(2,  "4539148803436467");              // PAN — Luhn-valid demo Visa
            msg.set(3,  "000000");                         // Processing code: purchase
            msg.set(4,  zeroPad(amountPaise, 12));         // Amount in paise
            msg.set(7,  now.format(MMDD_HHMMSS));          // Transmission date/time
            msg.set(11, zeroPad(stan, 6));                 // STAN
            msg.set(12, now.format(HHMMSS));               // Local transaction time
            msg.set(13, now.format(MMDD));                 // Local transaction date
            msg.set(18, "5411");                           // MCC: grocery stores
            msg.set(22, "051");                            // POS entry: chip + PIN contact EMV
            msg.set(25, "00");                             // POS condition: normal
            msg.set(35, "4539148803436467=2812");           // Track 2: PAN=Expiry
            msg.set(37, zeroPad("1", 12));                 // RRN — terminal-assigned
            msg.set(41, rightPad(config.terminalId(), 8)); // Terminal ID must be exactly 8 chars
            msg.set(42, rightPad(config.merchantId(), 15));// Merchant ID must be exactly 15 chars
            msg.set(43, rightPad("Test Merchant Pvt Ltd  IN", 40)); // Name(25) + City(13) + Country(2)
            msg.set(49, "356");                            // Currency code: INR
            return msg;
        } catch (ISOException e) {
            throw new IllegalStateException("Failed to build MTI 0100 message", e);
        }
    }

    /**
     * Builds an MTI 0400 Reversal Request.
     * Field 90 carries the original 0100's MTI, STAN, transmission date/time.
     *
     * @param config       terminal identity
     * @param stan         new STAN for this reversal message
     * @param amountPaise  amount to reverse (must match original 0100)
     * @param originalStan STAN from the original 0100 — links this reversal to that request
     */
    public ISOMsg buildReversalRequest(TerminalConfig config, String stan, String amountPaise, String originalStan) {
        LocalDateTime now = LocalDateTime.now();
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0400");
            msg.set(2,  "4539148803436467");
            msg.set(3,  "000000");
            msg.set(4,  zeroPad(amountPaise, 12));
            msg.set(7,  now.format(MMDD_HHMMSS));
            msg.set(11, zeroPad(stan, 6));
            msg.set(12, now.format(HHMMSS));
            msg.set(13, now.format(MMDD));
            msg.set(18, "5411");
            msg.set(22, "051");
            msg.set(37, zeroPad("1", 12));
            msg.set(41, rightPad(config.terminalId(), 8));
            msg.set(42, rightPad(config.merchantId(), 15));
            msg.set(49, "356");
            // Field 90: original data — origMTI(4) + origSTAN(6) + origDateTime(10) + padding
            msg.set(90, "0100" + zeroPad(originalStan, 6) + now.format(MMDD_HHMMSS) + "0".repeat(22));
            return msg;
        } catch (ISOException e) {
            throw new IllegalStateException("Failed to build MTI 0400 message", e);
        }
    }

    public GenericPackager getPackager() {
        return packager;
    }

    private static String zeroPad(String value, int length) {
        if (value.length() >= length) return value.substring(value.length() - length);
        return "0".repeat(length - value.length()) + value;
    }

    private static String rightPad(String value, int length) {
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }
}
