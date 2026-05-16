package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.SettlementBatch;

// LEARN: AdapterPort — batch file submission to network; domain submits SettlementBatch record
public interface SettlementPort {
    SettlementResult submit(SettlementBatch batch);
}
