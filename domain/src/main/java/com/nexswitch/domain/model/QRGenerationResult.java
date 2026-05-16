package com.nexswitch.domain.model;

import java.time.Instant;

// LEARN: SealedInterface — Generated/Failed exhaustive; caller cannot ignore failure path
public sealed interface QRGenerationResult
        permits QRGenerationResult.Generated,
                QRGenerationResult.Failed {

    record Generated(String txnRef, String qrImageBase64, Instant expiresAt) implements QRGenerationResult {}

    record Failed(String reason) implements QRGenerationResult {}
}
