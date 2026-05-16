package com.nexswitch.terminal.message;

import com.nexswitch.terminal.config.TerminalConfig;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Iso8583BuilderTest {

    private Iso8583Builder builder;
    private TerminalConfig config;

    @BeforeEach
    void setUp() {
        builder = new Iso8583Builder();
        config  = new TerminalConfig("TERM0042", "MERCH0000999", "localhost", 8000, 15000, "NORMAL_PURCHASE");
    }

    @Test
    void buildAuthRequest_setsMti0100() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        assertEquals("0100", msg.getMTI());
    }

    @Test
    void buildAuthRequest_setsTerminalIdField41() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        assertEquals("TERM0042", msg.getString(41).trim());
    }

    @Test
    void buildAuthRequest_setsMerchantIdPaddedTo15CharsField42() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        String merchantId = msg.getString(42);
        assertEquals(15, merchantId.length(), "Field 42 must be exactly 15 chars per ISO 8583 spec");
        assertTrue(merchantId.startsWith("MERCH0000999"), "Merchant ID value must be present");
    }

    @Test
    void buildAuthRequest_setsAmountField4() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        assertEquals("000000600000", msg.getString(4));
    }

    @Test
    void buildAuthRequest_setsStan6DigitsField11() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000042", "000000600000");
        assertEquals("000042", msg.getString(11));
    }

    @Test
    void buildAuthRequest_setsProcessingCode000000ForPurchaseField3() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        assertEquals("000000", msg.getString(3));
    }

    @Test
    void buildAuthRequest_setsCurrencyCodeInr356Field49() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        assertEquals("356", msg.getString(49));
    }

    @Test
    void buildAuthRequest_setsPosEntryModeChipPinField22() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        assertEquals("051", msg.getString(22));
    }

    @Test
    void buildAuthRequest_packAndUnpackRoundtripPreservesFields() throws ISOException {
        ISOMsg original = builder.buildAuthRequest(config, "000001", "000000600000");

        byte[] packed = original.pack();
        assertNotNull(packed);
        assertTrue(packed.length > 0);

        ISOMsg roundtripped = new ISOMsg();
        roundtripped.setPackager(original.getPackager());
        roundtripped.unpack(packed);

        assertEquals("0100",          roundtripped.getMTI());
        assertEquals("000000600000",  roundtripped.getString(4));
        assertEquals("000001",        roundtripped.getString(11));
        assertEquals("356",           roundtripped.getString(49));
        assertEquals("TERM0042",      roundtripped.getString(41).trim());
    }

    @Test
    void buildReversalRequest_setsMti0400() throws ISOException {
        ISOMsg msg = builder.buildReversalRequest(config, "000002", "000000600000", "000001");
        assertEquals("0400", msg.getMTI());
    }

    @Test
    void buildReversalRequest_setsOriginalDataElementsField90() throws ISOException {
        ISOMsg msg = builder.buildReversalRequest(config, "000002", "000000600000", "000001");
        String field90 = msg.getString(90);
        assertNotNull(field90, "Field 90 must be present in reversals");
        assertTrue(field90.startsWith("0100"), "Field 90 must start with original MTI 0100");
        assertTrue(field90.contains("000001"), "Field 90 must contain original STAN 000001");
    }

    @Test
    void buildAuthRequest_packProducesNonEmptyByteArray() throws ISOException {
        ISOMsg msg = builder.buildAuthRequest(config, "000001", "000000600000");
        byte[] packed = msg.pack();
        // 4 bytes MTI + 16 bytes bitmap + fields; minimum realistic message > 80 bytes
        assertTrue(packed.length > 80, "Packed 0100 message should be at least 80 bytes");
    }
}
