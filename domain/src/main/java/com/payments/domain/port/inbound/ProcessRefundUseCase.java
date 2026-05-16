package com.payments.domain.port.inbound;

import com.payments.domain.model.RefundResult;

// LEARN: DependencyInversion — use case interface is the inbound boundary of the hexagon
public interface ProcessRefundUseCase {
    RefundResult execute(RefundCommand command);
}
