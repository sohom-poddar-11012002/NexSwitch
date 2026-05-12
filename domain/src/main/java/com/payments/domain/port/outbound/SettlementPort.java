package com.payments.domain.port.outbound;

import com.payments.domain.model.SettlementBatch;

public interface SettlementPort {
    SettlementResult submit(SettlementBatch batch);
}
