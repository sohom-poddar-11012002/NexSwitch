package com.nexswitch.qa.application;

import com.nexswitch.qa.adapter.sse.SseEventPublisher;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import com.nexswitch.qa.domain.service.AssertionEvaluator;
import com.nexswitch.qa.domain.service.ScenarioExecutionEngine;
import com.nexswitch.qa.domain.service.VariableResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class QaOrchestratorConfig {

    @Bean
    public VariableResolver variableResolver() {
        return new VariableResolver();
    }

    @Bean
    public AssertionEvaluator assertionEvaluator() {
        return new AssertionEvaluator();
    }

    @Bean
    public ScenarioExecutionEngine scenarioExecutionEngine(
            List<TestChannelPort> channels,
            ExecutionEventPublisher eventPublisher,
            VariableResolver variableResolver,
            AssertionEvaluator assertionEvaluator) {
        return new ScenarioExecutionEngine(channels, eventPublisher, variableResolver, assertionEvaluator);
    }

    // SseEventPublisher is @Component but also needs to be the ExecutionEventPublisher bean
    @Bean
    public ExecutionEventPublisher executionEventPublisher(SseEventPublisher sseEventPublisher) {
        return sseEventPublisher;
    }
}
