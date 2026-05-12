package com.payments.domain.port.inbound;

import com.payments.domain.model.ReversalResult;

public interface ProcessReversalUseCase {
    ReversalResult execute(ReversalCommand command);
}
