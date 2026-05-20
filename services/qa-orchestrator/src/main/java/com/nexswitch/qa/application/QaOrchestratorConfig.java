package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.port.inbound.TriggerRunUseCase;
import com.nexswitch.qa.domain.port.outbound.NotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import com.nexswitch.qa.domain.port.outbound.ExpressionEvaluator;
import com.nexswitch.qa.domain.port.outbound.RunExecutionRepository;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import com.nexswitch.qa.domain.service.ScenarioExecutionEngine;
import com.nexswitch.qa.domain.service.VariableResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
@EnableScheduling
public class QaOrchestratorConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationPort.class)
    public NotificationPort noopNotificationAdapter() {
        return new NoopNotificationAdapter();
    }

    @Bean
    public VariableResolver variableResolver() {
        return new VariableResolver();
    }

    @Bean
    public ExpressionEvaluator expressionEvaluator() {
        return new AssertionEvaluator();
    }

    @Bean
    public ScenarioExecutionEngine scenarioExecutionEngine(
            List<TestChannelPort> channels,
            ExecutionEventPublisher eventPublisher,
            VariableResolver variableResolver,
            ExpressionEvaluator expressionEvaluator) {
        return new ScenarioExecutionEngine(channels, eventPublisher, variableResolver, expressionEvaluator);
    }

    @Bean
    public TriggerSuiteService triggerSuiteService(
            ScenarioRepository scenarioRepository,
            TriggerRunUseCase triggerRun,
            RunExecutionRepository executionRepository,
            ExecutionEventPublisher eventPublisher) {
        return new TriggerSuiteService(scenarioRepository, triggerRun, executionRepository, eventPublisher);
    }
}
