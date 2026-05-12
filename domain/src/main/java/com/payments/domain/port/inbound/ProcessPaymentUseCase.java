package com.payments.domain.port.inbound;

import com.payments.domain.model.AuthorizationResult;

public interface ProcessPaymentUseCase {
    AuthorizationResult execute(AuthorizationCommand command);
}
