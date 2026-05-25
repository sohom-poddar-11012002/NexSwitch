package com.nexswitch.adapters.outbound.network;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.ReversalResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.port.outbound.AuthorizationPort;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// LEARN: Fail-fast fallback — when the network circuit is OPEN the adapter returns a well-typed
//        Declined/Failed result rather than blocking. The ISO 8583 handler maps this to Field 39
//        code "91" (issuer/switch inoperative) and the terminal queues for retry. Without the
//        circuit breaker each 0100 would sit for 15s before timing out; with it, rejection is
//        sub-millisecond, protecting downstream threads and the Netty event loop.
@Primary
@Component
public class ResilientNetworkAuthAdapter implements AuthorizationPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientNetworkAuthAdapter.class);

    private static final String CB_NAME      = "network";
    private static final String BH_NAME      = "network";
    private static final String RETRY_NAME   = "networkAuth";
    private static final String RC_SWITCH_INOP = "91";

    private final AuthorizationPort delegate;

    public ResilientNetworkAuthAdapter(
            @Qualifier("mockNetworkAuthAdapter") AuthorizationPort delegate) {
        this.delegate = delegate;
    }

    // LEARN: Retry wraps CircuitBreaker (inner-to-outer: CB > Retry > Bulkhead per Resilience4j
    //        aspect order). Transient timeouts are retried once; if both attempts fail the CB
    //        records the failure. When CB opens, authorizeFallback fires immediately (no retry).
    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "authorizeFallback")
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public AuthorizationResult authorize(Transaction transaction) {
        return delegate.authorize(transaction);
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "reverseFallback")
    @Bulkhead(name = BH_NAME, type = Bulkhead.Type.SEMAPHORE)
    @Override
    public ReversalResult reverse(Transaction transaction) {
        return delegate.reverse(transaction);
    }

    // Fallback fires when CB is OPEN or bulkhead is full
    public AuthorizationResult authorizeFallback(Transaction transaction, Throwable t) {
        log.warn("network.circuit_open.authorize txnId={} cause={}", transaction.id(), t.getClass().getSimpleName());
        return new AuthorizationResult.Declined(RC_SWITCH_INOP, "UPSTREAM_UNAVAILABLE");
    }

    public ReversalResult reverseFallback(Transaction transaction, Throwable t) {
        log.warn("network.circuit_open.reverse txnId={} cause={}", transaction.id(), t.getClass().getSimpleName());
        return new ReversalResult.Failed("UPSTREAM_UNAVAILABLE");
    }
}
