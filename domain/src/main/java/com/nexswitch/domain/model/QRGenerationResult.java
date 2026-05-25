package com.nexswitch.domain.model;

import com.nexswitch.domain.model.vo.TxnRef;

import java.time.Instant;

// LEARN: SealedInterface — Generated/Failed exhaustive; caller cannot ignore failure path
public sealed interface QRGenerationResult
        permits QRGenerationResult.Generated,
                QRGenerationResult.Failed {

    record Generated(TxnRef txnRef, String qrImageBase64, Instant expiresAt) implements QRGenerationResult {}

    record Failed(String reason) implements QRGenerationResult {}
}
