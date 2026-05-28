package com.nexswitch.adapters.outbound.settlement;

import com.nexswitch.domain.model.SettlementBatch;
import com.nexswitch.domain.port.outbound.SettlementPort;
import com.nexswitch.domain.port.outbound.SettlementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

// LEARN: Stub adapter — production wires real Visa/MC network submission via HTTPS REST API
//        with HMAC-signed batch files. Stub logs intent so settlement flow compiles and runs
//        end-to-end in local dev without requiring a Visa certification agreement.
@Component
public class StubSettlementPortAdapter implements SettlementPort {

    private static final Logger log = LoggerFactory.getLogger(StubSettlementPortAdapter.class);

    @Override
    public SettlementResult submit(SettlementBatch batch) {
        String batchId = "BATCH-" + batch.batchDate() + "-STUB";
        log.warn("settlement.port.stub — NOT wired to real network. batchId={} network={} txnCount={} amount={}",
                batchId, batch.network(), batch.transactionCount(), batch.grossAmount());
        return new SettlementResult.Submitted(batchId, Instant.now());
    }
}
