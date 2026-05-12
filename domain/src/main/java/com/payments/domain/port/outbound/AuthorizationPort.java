package com.payments.domain.port.outbound;

import com.payments.domain.model.AuthorizationResult;
import com.payments.domain.model.ReversalResult;
import com.payments.domain.model.Transaction;

public interface AuthorizationPort {
    AuthorizationResult authorize(Transaction transaction);
    ReversalResult reverse(Transaction transaction);
}
