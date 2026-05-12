package com.payments.domain.port.inbound;

import com.payments.domain.model.QRGenerationResult;

public interface GenerateQRUseCase {
    QRGenerationResult execute(QRGenerationCommand command);
}
