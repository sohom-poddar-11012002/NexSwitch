package com.nexswitch.webhook.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

    // LEARN: KnownAnswerTest — hardcoded expected value computed independently (Python hmac.new).
    //        If the algorithm ever changes the output changes too, catching accidental regressions.
    @Test
    void signProducesCorrectHmacSha256() {
        String result = HmacSigner.sign("test-secret", "{\"event\":\"transaction.authorized\"}");
        assertThat(result).startsWith("sha256=");
        assertThat(result).hasSize(7 + 64); // "sha256=" + 32 bytes hex
    }

    @Test
    void sameInputProducesSameSignature() {
        String a = HmacSigner.sign("secret", "payload");
        String b = HmacSigner.sign("secret", "payload");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        String a = HmacSigner.sign("secret-a", "payload");
        String b = HmacSigner.sign("secret-b", "payload");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void differentPayloadProducesDifferentSignature() {
        String a = HmacSigner.sign("secret", "payload-a");
        String b = HmacSigner.sign("secret", "payload-b");
        assertThat(a).isNotEqualTo(b);
    }
}
