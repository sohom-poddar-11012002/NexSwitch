package com.payments.domain.port.inbound;

import com.payments.domain.model.RefundResult;

public interface ProcessRefundUseCase {
    RefundResult execute(RefundCommand command);
}
