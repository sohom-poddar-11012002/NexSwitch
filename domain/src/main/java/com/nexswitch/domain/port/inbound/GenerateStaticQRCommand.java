package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.vo.MerchantId;

import java.util.Objects;

public record GenerateStaticQRCommand(MerchantId merchantId) {
    public GenerateStaticQRCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
    }
}
