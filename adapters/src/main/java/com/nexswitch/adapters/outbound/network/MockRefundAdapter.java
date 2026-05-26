package com.nexswitch.adapters.outbound.network;

import com.nexswitch.domain.model.RefundResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.vo.Money;
import com.nexswitch.domain.port.outbound.RefundPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

// LEARN: ConditionalOnProperty — same Strategy pattern as HSM; swap mock for ISO-8583 reversal
//        adapter in staging without changing domain code or AdapterConfig.
@Component
@ConditionalOnProperty(name = "upstream.provider", havingValue = "wiremock", matchIfMissing = true)
public class MockRefundAdapter implements RefundPort {

    private static final Logger log = LoggerFactory.getLogger(MockRefundAdapter.class);

    @Override
    public RefundResult requestRefund(Transaction transaction, Money refundAmount) {
        UUID refundId = UUID.randomUUID();
        log.info("refund.mock.initiated refundId={} transactionId={} amount={}",
                refundId, transaction.id(), refundAmount.amount());
        return new RefundResult.Initiated(refundId, Instant.now());
    }
}
