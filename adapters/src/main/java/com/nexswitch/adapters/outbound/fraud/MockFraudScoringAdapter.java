package com.nexswitch.adapters.outbound.fraud;

import com.nexswitch.domain.model.FraudScoringContext;
import com.nexswitch.domain.port.outbound.FraudScoringPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

// LEARN: FailOpen — returning Optional.empty() is the deliberate fail-open contract.
//        The domain service treats absent score as "no ML signal; forward to network."
//        In production this calls a LangGraph + Claude Haiku scoring endpoint with a
//        500ms timeout. The mock always returns empty so no transaction is blocked by ML.
@Component
@ConditionalOnProperty(name = "fraud.scoring.enabled", havingValue = "false", matchIfMissing = true)
public class MockFraudScoringAdapter implements FraudScoringPort {

    @Override
    public Optional<BigDecimal> score(FraudScoringContext context, Duration budget) {
        return Optional.empty();
    }
}
