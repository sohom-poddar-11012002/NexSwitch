package com.nexswitch.adapters.outbound.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.nexswitch.domain.port.outbound.QrImageGeneratorPort;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

// LEARN: ZXing uses a BitMatrix (2D boolean grid) to represent QR module positions.
//        MatrixToImageWriter converts it to a BufferedImage; ImageIO encodes to PNG bytes.
//        Error correction level M allows ~15% of data to be restored if the QR is damaged —
//        standard for payment QRs where dirty POS screens are common.
@Component
public class ZxingQrImageGeneratorAdapter implements QrImageGeneratorPort {

    private static final QRCodeWriter WRITER = new QRCodeWriter();

    @Override
    public String generateBase64Png(String content, int widthPx, int heightPx) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1
            );
            BitMatrix matrix = WRITER.encode(content, BarcodeFormat.QR_CODE, widthPx, heightPx, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException e) {
            throw new IllegalStateException("QR code generation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("QR PNG encoding failed: " + e.getMessage(), e);
        }
    }
}
