package com.nexswitch.domain.port.outbound;

import com.nexswitch.domain.model.FraudScoringContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

// LEARN: OptionalPort — returns Optional; absent when ML service not wired (pre-June)
public interface FraudScoringPort {
    /**
     * Returns an ML risk probability in [0.0, 1.0], or empty if unavailable within budget.
     * Never throws — caller must treat empty as "no ML signal available".
     */
    Optional<BigDecimal> score(FraudScoringContext context, Duration budget);
}
