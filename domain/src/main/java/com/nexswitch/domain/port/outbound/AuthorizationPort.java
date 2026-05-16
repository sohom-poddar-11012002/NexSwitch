package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.ReversalResult;
import com.nexswitch.domain.model.Transaction;

// LEARN: AdapterPort — network authorization abstracted; Visa adapter and MockAdapter implement same interface
public interface AuthorizationPort {
    AuthorizationResult authorize(Transaction transaction);
    ReversalResult reverse(Transaction transaction);
}
