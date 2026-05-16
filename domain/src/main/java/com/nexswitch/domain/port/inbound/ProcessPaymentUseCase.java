package com.nexswitch.domain.port.inbound;

import com.nexswitch.domain.model.AuthorizationResult;

// LEARN: DependencyInversion — application layer depends on this interface, not on domain service impl
public interface ProcessPaymentUseCase {
    AuthorizationResult execute(AuthorizationCommand command);
}
