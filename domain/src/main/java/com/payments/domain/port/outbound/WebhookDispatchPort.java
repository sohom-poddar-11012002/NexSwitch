package com.payments.domain.port.outbound;

import com.payments.domain.model.vo.MerchantId;

public interface WebhookDispatchPort {
    void dispatch(MerchantId merchantId, String eventType, String payloadJson);
}
