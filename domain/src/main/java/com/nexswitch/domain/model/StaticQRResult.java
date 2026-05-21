package com.nexswitch.domain.model;

public sealed interface StaticQRResult
        permits StaticQRResult.Generated, StaticQRResult.Failed {

    record Generated(String qrImageBase64, String vpa, String upiString) implements StaticQRResult {}
    record Failed(String reason) implements StaticQRResult {}
}
