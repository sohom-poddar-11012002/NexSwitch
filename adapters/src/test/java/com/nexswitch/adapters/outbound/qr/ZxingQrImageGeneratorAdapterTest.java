package com.nexswitch.adapters.outbound.qr;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class ZxingQrImageGeneratorAdapterTest {

    private final ZxingQrImageGeneratorAdapter adapter = new ZxingQrImageGeneratorAdapter();

    @Test
    void generateBase64Png_returnsNonEmptyBase64() {
        String upi = "upi://pay?pa=merchant%40payswiff&pn=Test+Merchant&tr=TXN001&am=6000.00&cu=INR";

        String result = adapter.generateBase64Png(upi, 300, 300);

        assertThat(result).isNotBlank();
        // Verify it's valid Base64 by decoding
        byte[] bytes = Base64.getDecoder().decode(result);
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void generateBase64Png_outputIsValidPngMagicBytes() {
        String result = adapter.generateBase64Png("test-content", 100, 100);

        byte[] bytes = Base64.getDecoder().decode(result);
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(bytes[0] & 0xFF).isEqualTo(0x89);
        assertThat(bytes[1] & 0xFF).isEqualTo(0x50); // 'P'
        assertThat(bytes[2] & 0xFF).isEqualTo(0x4E); // 'N'
        assertThat(bytes[3] & 0xFF).isEqualTo(0x47); // 'G'
    }

    @Test
    void generateBase64Png_differentContentProducesDifferentOutput() {
        String qr1 = adapter.generateBase64Png("upi://pay?tr=TXN001&am=100.00", 300, 300);
        String qr2 = adapter.generateBase64Png("upi://pay?tr=TXN002&am=200.00", 300, 300);

        assertThat(qr1).isNotEqualTo(qr2);
    }
}
