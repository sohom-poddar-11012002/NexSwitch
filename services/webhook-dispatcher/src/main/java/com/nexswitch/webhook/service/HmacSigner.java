package com.nexswitch.webhook.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

// LEARN: HMAC-SHA256 — keyed hash; only a party that holds the secret can produce or verify the signature.
//        Stripe, Razorpay, and every major gateway use this pattern: the merchant stores the secret,
//        hashes the raw request body, and compares against X-Signature header to reject forged deliveries.
public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {}

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
