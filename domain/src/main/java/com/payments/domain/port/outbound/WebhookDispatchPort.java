package com.payments.domain.port.outbound;

import com.payments.domain.model.vo.MerchantId;

// LEARN: AdapterPort — HMAC signing happens in adapter; domain only calls dispatch()
public interface WebhookDispatchPort {
    void dispatch(MerchantId merchantId, String eventType, String payloadJson);
}
