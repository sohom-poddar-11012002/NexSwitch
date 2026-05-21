package com.nexswitch.adapters.outbound;

import com.nexswitch.adapters.outbound.network.ResilientNetworkAuthAdapter;
import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.ReversalResult;
import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.vo.*;
import com.nexswitch.domain.port.outbound.AuthorizationPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// LEARN: Without a Spring context the @CircuitBreaker AOP interceptor is inactive, so we test
//        the two units separately: (1) happy-path delegation, (2) fallback methods directly.
//        Integration tests (tagged "integration") cover the full AOP proxy behaviour.
@ExtendWith(MockitoExtension.class)
class ResilientAdaptersTest {

    @Mock AuthorizationPort delegate;

    // ── Delegation: delegate is called for normal flow ────────────────────────

    @Test
    void givenHealthyDelegate_whenAuthorize_thenDelegateCalled() {
        var adapter = new ResilientNetworkAuthAdapter(delegate);
        var txn = transaction();
        when(delegate.authorize(txn))
            .thenReturn(new AuthorizationResult.Approved(AuthorizationCode.of("000000"), Instant.now(), null));

        AuthorizationResult result = adapter.authorize(txn);

        assertThat(result).isInstanceOf(AuthorizationResult.Approved.class);
        verify(delegate).authorize(txn);
    }

    @Test
    void givenHealthyDelegate_whenReverse_thenDelegateCalled() {
        var adapter = new ResilientNetworkAuthAdapter(delegate);
        var txn = transaction();
        when(delegate.reverse(txn))
            .thenReturn(new ReversalResult.Accepted(Instant.now()));

        ReversalResult result = adapter.reverse(txn);

        assertThat(result).isInstanceOf(ReversalResult.Accepted.class);
        verify(delegate).reverse(txn);
    }

    // ── Fallback: CB open → well-typed decline/fail ───────────────────────────

    @Test
    void whenCircuitOpen_authorizeFallbackReturnsDeclined91() {
        var adapter = new ResilientNetworkAuthAdapter(delegate);
        var txn = transaction();
        // Simulate what Resilience4j calls when the circuit is OPEN
        CallNotPermittedException openException =
            CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("network"));

        AuthorizationResult result = adapter.authorizeFallback(txn, openException);

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("91");
        assertThat(((AuthorizationResult.Declined) result).reason()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    @Test
    void whenCircuitOpen_reverseFallbackReturnsFailed() {
        var adapter = new ResilientNetworkAuthAdapter(delegate);
        var txn = transaction();
        CallNotPermittedException openException =
            CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("network"));

        ReversalResult result = adapter.reverseFallback(txn, openException);

        assertThat(result).isInstanceOf(ReversalResult.Failed.class);
        assertThat(((ReversalResult.Failed) result).reason()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    // ── Fallback also fires on runtime exceptions from delegate ───────────────

    @Test
    void whenDelegateThrows_authorizeFallbackHandlesRuntimeException() {
        var adapter = new ResilientNetworkAuthAdapter(delegate);
        var txn = transaction();

        AuthorizationResult result = adapter.authorizeFallback(txn, new RuntimeException("timeout"));

        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
        assertThat(((AuthorizationResult.Declined) result).responseCode()).isEqualTo("91");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Transaction transaction() {
        return Transaction.initiate(
            UUID.randomUUID(),
            MerchantId.of("MERCH0000001"),
            TerminalId.of("TERM0001"),
            Money.of(new BigDecimal("6000.00"), Currency.getInstance("INR")),
            PaymentNetwork.VISA,
            PaymentMethod.CARD_CHIP,
            PanHash.fromRawPan("4539148803436467"),
            SystemTraceAuditNumber.of("000001"),
            Instant.now()
        );
    }
}
