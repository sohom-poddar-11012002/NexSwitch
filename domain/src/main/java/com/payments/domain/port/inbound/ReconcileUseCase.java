package com.payments.domain.port.inbound;

import com.payments.domain.model.ReconciliationResult;

// LEARN: DependencyInversion — batch job calls this interface; domain doesn't know about Spring Batch
public interface ReconcileUseCase {
    ReconciliationResult execute(ReconciliationCommand command);
}
