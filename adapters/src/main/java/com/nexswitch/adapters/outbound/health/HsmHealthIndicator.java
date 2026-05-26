package com.nexswitch.adapters.outbound.health;

import com.nexswitch.domain.port.outbound.HsmPort;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// LEARN: Custom HealthIndicator — Spring's auto-configured disk/db/redis indicators don't know
//        about HSMs. This contributes "hsm" to /actuator/health so the readiness probe can
//        reject traffic when the HSM slot is closed or the PKCS#11 library is unavailable.
@Component
public class HsmHealthIndicator implements HealthIndicator {

    private final HsmPort hsmPort;

    public HsmHealthIndicator(HsmPort hsmPort) {
        this.hsmPort = hsmPort;
    }

    @Override
    public Health health() {
        try {
            // LEARN: ping() in a CompletableFuture with 500ms timeout — a hung PKCS#11 library
            //        would otherwise block the actuator thread indefinitely, causing /health itself
            //        to time out. 500ms matches the HSM timeout defined in CLAUDE.md §4 rule 4.
            boolean up = CompletableFuture
                    .supplyAsync(hsmPort::ping)
                    .get(500, TimeUnit.MILLISECONDS);
            return up ? Health.up().build()
                      : Health.down().withDetail("reason", "ping returned false").build();
        } catch (TimeoutException e) {
            return Health.down().withDetail("reason", "ping timed out after 500ms").build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
    }
}
