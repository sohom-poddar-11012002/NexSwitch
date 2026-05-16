package com.nexswitch.domain.model.vo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// LEARN: PCI-DSS — SHA-256 applied at terminal boundary; raw PAN never stored or logged after fromRawPan()
public record PanHash(String value) {

    private static final int SHA256_HEX_LENGTH = 64;

    public PanHash {
        if (value == null)
            throw new IllegalArgumentException("PanHash value must not be null");
        String normalised = value.toLowerCase();
        if (normalised.length() != SHA256_HEX_LENGTH)
            throw new IllegalArgumentException(
                "PanHash must be a 64-character SHA-256 hex string, got length: " + normalised.length());
        if (!normalised.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(
                "PanHash must contain only hex characters [a-f0-9]: " + normalised);
        value = normalised;
    }

    public static PanHash of(String hexHash) {
        return new PanHash(hexHash);
    }

    public static PanHash fromRawPan(String pan) {
        if (pan == null || pan.isBlank())
            throw new IllegalArgumentException("PAN must not be null or empty");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(pan.getBytes(StandardCharsets.UTF_8));
            return new PanHash(bytesToHex(hashBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PanHash{" + value.substring(0, 8) + "...}";
    }
}
