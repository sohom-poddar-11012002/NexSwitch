package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.StaticQRResult;

public interface GenerateStaticQRUseCase {
    StaticQRResult execute(GenerateStaticQRCommand command);
}
