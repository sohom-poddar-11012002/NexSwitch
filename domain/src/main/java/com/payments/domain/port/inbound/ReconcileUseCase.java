package com.payments.domain.port.inbound;

import com.payments.domain.model.ReconciliationResult;

public interface ReconcileUseCase {
    ReconciliationResult execute(ReconciliationCommand command);
}
