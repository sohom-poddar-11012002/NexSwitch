package com.nexswitch.adapters.inbound.metrics;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.model.PaymentMethod;
import com.nexswitch.domain.model.PaymentNetwork;
import com.nexswitch.domain.model.vo.*;
import com.nexswitch.domain.port.inbound.AuthorizationCommand;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentMetricsAspectTest {

    private MeterRegistry registry;
    private PaymentMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        aspect = new PaymentMetricsAspect(registry);
    }

    @Test
    void incrementsAuthorizedCounter() throws Throwable {
        ProceedingJoinPoint pjp = mockPjp(AuthorizationResult.Approved.class);

        aspect.recordPaymentMetrics(pjp);

        assertThat(registry.counter("payment_authorized_total",
                "scheme", "visa", "currency", "INR").count()).isEqualTo(1.0);
    }

    @Test
    void incrementsDeclinedCounter() throws Throwable {
        ProceedingJoinPoint pjp = mockPjpWithResult(
                new AuthorizationResult.Declined("51", "INSUFFICIENT_FUNDS"));

        aspect.recordPaymentMetrics(pjp);

        assertThat(registry.counter("payment_declined_total",
                "scheme", "visa", "decline_reason", "INSUFFICIENT_FUNDS").count()).isEqualTo(1.0);
    }

    @Test
    void incrementsFraudBlockedCounter() throws Throwable {
        ProceedingJoinPoint pjp = mockPjpWithResult(
                new AuthorizationResult.Blocked("HIGH_FRAUD_SCORE:0.95"));

        aspect.recordPaymentMetrics(pjp);

        assertThat(registry.counter("fraud_blocked_total", "scheme", "visa").count()).isEqualTo(1.0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ProceedingJoinPoint mockPjp(Class<? extends AuthorizationResult> resultType) throws Throwable {
        AuthorizationResult result = resultType == AuthorizationResult.Approved.class
                ? new AuthorizationResult.Approved(new AuthorizationCode("123456"), Instant.now(), null)
                : new AuthorizationResult.Declined("99", "UNKNOWN");
        return mockPjpWithResult(result);
    }

    private ProceedingJoinPoint mockPjpWithResult(AuthorizationResult result) throws Throwable {
        AuthorizationCommand cmd = new AuthorizationCommand(
                UUID.randomUUID(),
                new MerchantId("MERCH001"),
                new TerminalId("TERM0001"),
                "411111",
                new PanHash("a".repeat(64)),
                Money.of(new BigDecimal("1000.00"), Currency.getInstance("INR")),
                PaymentNetwork.VISA,
                PaymentMethod.CARD_CHIP,
                new SystemTraceAuditNumber("000001"),
                null, null, null, "051");
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{cmd});
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }
}
