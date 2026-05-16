package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.QRGenerationResult;

// LEARN: DependencyInversion — QR generation use case; ZXing lives in adapter, not here
public interface GenerateQRUseCase {
    QRGenerationResult execute(QRGenerationCommand command);
}
