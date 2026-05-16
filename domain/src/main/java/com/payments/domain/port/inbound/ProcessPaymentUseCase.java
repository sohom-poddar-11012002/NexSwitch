package com.payments.domain.port.inbound;

import com.payments.domain.model.AuthorizationResult;

// LEARN: DependencyInversion — application layer depends on this interface, not on domain service impl
public interface ProcessPaymentUseCase {
    AuthorizationResult execute(AuthorizationCommand command);
}
