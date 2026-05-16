package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.ReversalResult;

// LEARN: DependencyInversion — adapter calls this; domain never depends on adapter
public interface ProcessReversalUseCase {
    ReversalResult execute(ReversalCommand command);
}
