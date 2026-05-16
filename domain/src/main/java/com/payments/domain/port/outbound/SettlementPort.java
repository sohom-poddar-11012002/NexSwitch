package com.payments.domain.port.outbound;

import com.payments.domain.model.SettlementBatch;

// LEARN: AdapterPort — batch file submission to network; domain submits SettlementBatch record
public interface SettlementPort {
    SettlementResult submit(SettlementBatch batch);
}
