package com.payments.domain.port.inbound;

import com.payments.domain.model.ReversalResult;

// LEARN: DependencyInversion — adapter calls this; domain never depends on adapter
public interface ProcessReversalUseCase {
    ReversalResult execute(ReversalCommand command);
}
