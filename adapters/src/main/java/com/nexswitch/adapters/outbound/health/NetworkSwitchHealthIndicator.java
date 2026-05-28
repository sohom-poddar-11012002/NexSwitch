package com.nexswitch.adapters.outbound.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// LEARN: Custom HealthIndicator for payment networks — Spring's built-in indicators don't know about
//        Visa/Mastercard TCP connections. This contributes "networkSwitch" to /actuator/health so
//        readiness probes can reject traffic when all payment network circuit breakers are open.
@Component
public class NetworkSwitchHealthIndicator implements HealthIndicator {

    // LEARN: Circuit breaker state — OPEN means all calls are failing fast; HALF_OPEN means one
    //        test call is being allowed through; CLOSED means the network is healthy.
    //        Reporting OPEN circuits in health lets load balancers route to a healthy pod.
    private static final List<String> NETWORKS = List.of("VISA", "MASTERCARD", "RUPAY");

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    public NetworkSwitchHealthIndicator(
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry          = meterRegistry;
        registerGauges();
    }

    @Override
    public Health health() {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        boolean anyUp = false;

        for (String network : NETWORKS) {
            try {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(network.toLowerCase() + "-network");
                CircuitBreaker.State state = cb.getState();
                details.put(network, state.name());
                if (state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN) {
                    anyUp = true;
                }
            } catch (Exception e) {
                // Circuit breaker not registered — assume reachable (no data = optimistic)
                details.put(network, "UNKNOWN");
                anyUp = true;
            }
        }

        details.put("status", anyUp ? "reachable" : "all-circuits-open");

        return anyUp
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }

    private void registerGauges() {
        for (String network : NETWORKS) {
            final String net = network;
            Gauge.builder("payment_network_up", () -> isNetworkUp(net))
                    .tag("network", network)
                    .description("1 if payment network circuit is not OPEN, 0 if OPEN")
                    .register(meterRegistry);
        }
    }

    private double isNetworkUp(String network) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(network.toLowerCase() + "-network");
            return cb.getState() == CircuitBreaker.State.OPEN ? 0.0 : 1.0;
        } catch (Exception e) {
            return 1.0; // Not found → assume up
        }
    }
}
