package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.ReversalResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;

// LEARN: AdapterPort — network authorization abstracted; Visa adapter and MockAdapter implement same interface
public interface AuthorizationPort {
    AuthorizationResult authorize(Transaction transaction);
    ReversalResult reverse(Transaction transaction);
    // LEARN: 0120 status inquiry — sent when the switch did not receive a definitive 0110 response.
    //        Returns the resolved status (AUTHORIZED/DECLINED) or null if the network cannot resolve it yet.
    TransactionStatus inquireStatus(Transaction transaction);
}
