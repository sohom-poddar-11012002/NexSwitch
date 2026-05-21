package com.nexswitch.adapters.outbound.network;

import com.nexswitch.domain.model.CollectRequest;
import com.nexswitch.domain.port.outbound.UpiPspNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// LEARN: UPI Collect stub — in local dev there is no live NPCI PSP connection.
//        The real outcome (APPROVE/REJECT) arrives asynchronously via POST /upi/collect/outcome.
//        This stub logs the collect request; a real adapter would POST to Mock NPCI's REST API.
@Component
public class MockNpciPspAdapter implements UpiPspNotifier {

    private static final Logger log = LoggerFactory.getLogger(MockNpciPspAdapter.class);

    @Override
    public void sendCollectRequest(CollectRequest request) {
        log.info("mock_npci.collect_sent collectId={} payerVpa={} amount={}",
                request.collectId(), request.payerVpa(), request.amount().amount());
    }
}
