package com.nexswitch.adapters.outbound.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class NetworkSwitchHealthIndicatorTest {

    // LEARN: SimpleMeterRegistry is a zero-dependency in-memory registry — avoids the full
    //        Spring/Prometheus bootstrap in unit tests while exercising Gauge registration.

    @Test
    void health_returnsUp_whenAllCircuitBreakersAreClosed() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        // Create closed circuit breakers for all three networks
        registry.circuitBreaker("visa-network");
        registry.circuitBreaker("mastercard-network");
        registry.circuitBreaker("rupay-network");

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        NetworkSwitchHealthIndicator indicator = new NetworkSwitchHealthIndicator(registry, meterRegistry);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("status");
        assertThat(health.getDetails().get("status")).isEqualTo("reachable");
    }

    @Test
    void health_returnsUp_whenCircuitBreakersNotRegistered() {
        // Empty registry — indicator should be optimistic (assume reachable)
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        NetworkSwitchHealthIndicator indicator = new NetworkSwitchHealthIndicator(registry, meterRegistry);

        Health health = indicator.health();

        // Without explicit circuit breakers, all UNKNOWN → anyUp = true
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_returnsDown_whenAllCircuitsOpen() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        // Open all three circuit breakers by transitioning to OPEN state
        openCircuitBreaker(registry, "visa-network");
        openCircuitBreaker(registry, "mastercard-network");
        openCircuitBreaker(registry, "rupay-network");

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        NetworkSwitchHealthIndicator indicator = new NetworkSwitchHealthIndicator(registry, meterRegistry);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("status")).isEqualTo("all-circuits-open");
    }

    @Test
    void constructor_registersGaugesForAllNetworks() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new NetworkSwitchHealthIndicator(registry, meterRegistry);

        // Three gauges registered — one per network
        assertThat(meterRegistry.find("payment_network_up").tag("network", "VISA").gauge()).isNotNull();
        assertThat(meterRegistry.find("payment_network_up").tag("network", "MASTERCARD").gauge()).isNotNull();
        assertThat(meterRegistry.find("payment_network_up").tag("network", "RUPAY").gauge()).isNotNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void openCircuitBreaker(CircuitBreakerRegistry registry, String name) {
        CircuitBreaker cb = registry.circuitBreaker(name,
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(1)
                        .minimumNumberOfCalls(1)
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .build());
        // Force OPEN state by recording failures beyond threshold
        cb.transitionToOpenState();
    }
}
