package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.RefundResult;

// LEARN: DependencyInversion — use case interface is the inbound boundary of the hexagon
public interface ProcessRefundUseCase {
    RefundResult execute(RefundCommand command);
}
