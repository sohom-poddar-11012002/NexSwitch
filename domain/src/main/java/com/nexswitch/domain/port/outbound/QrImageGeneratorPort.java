package com.nexswitch.domain.port.outbound;

// LEARN: DependencyInversion — ZXing, PNG encoding, Base64 are adapter concerns.
//        The domain just says "turn this string into a Base64 PNG"; it doesn't know how.
public interface QrImageGeneratorPort {
    String generateBase64Png(String content, int widthPx, int heightPx);
}
