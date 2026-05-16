package com.payments.domain.port.inbound;

import com.payments.domain.model.QRGenerationResult;

// LEARN: DependencyInversion — QR generation use case; ZXing lives in adapter, not here
public interface GenerateQRUseCase {
    QRGenerationResult execute(QRGenerationCommand command);
}
