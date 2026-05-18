package com.nexswitch.qa.domain.service;

import com.nexswitch.qa.application.AssertionEvaluator;
import com.nexswitch.qa.domain.model.*;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import com.nexswitch.qa.domain.port.outbound.TestChannelPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScenarioExecutionEngineTest {

    @Mock ExecutionEventPublisher eventPublisher;
    @Mock TestChannelPort         iso8583Channel;

    private ScenarioExecutionEngine engine;

    @BeforeEach
    void setUp() {
        when(iso8583Channel.supports(ChannelType.ISO8583)).thenReturn(true);
        engine = new ScenarioExecutionEngine(
                List.of(iso8583Channel),
                eventPublisher,
                new VariableResolver(),
                new AssertionEvaluator());
    }

    @Test
    void passes_when_send_and_assert_succeed() throws Exception {
        when(iso8583Channel.execute(any(), any()))
                .thenReturn(new StepResult.Passed("send_0", Duration.ofMillis(50),
                        Map.of("field39", "00")));

        TestScenario scenario = scenario(List.of(
                new TestStep.Send(ChannelType.ISO8583, "auth_request_0100",
                        Map.of("pan", "4539148803436467"), Duration.ofSeconds(5), "auth_response"),
                new TestStep.Assert("field39 == '00'", "Response code approved", true)
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stepExecutions()).hasSize(2);
        assertThat(result.stepExecutions().get(0).result()).isInstanceOf(StepResult.Passed.class);
        assertThat(result.stepExecutions().get(1).result()).isInstanceOf(StepResult.Passed.class);
    }

    @Test
    void fails_when_assert_expression_false() throws Exception {
        when(iso8583Channel.execute(any(), any()))
                .thenReturn(new StepResult.Passed("send_0", Duration.ofMillis(50),
                        Map.of("field39", "51")));

        TestScenario scenario = scenario(List.of(
                new TestStep.Send(ChannelType.ISO8583, "auth_request_0100",
                        Map.of(), Duration.ofSeconds(5), "auth_response"),
                new TestStep.Assert("field39 == '00'", "Must be approved", false)
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void fail_fast_skips_subsequent_steps() throws Exception {
        when(iso8583Channel.execute(any(), any()))
                .thenReturn(new StepResult.Passed("send_0", Duration.ofMillis(50),
                        Map.of("field39", "51")));

        TestScenario scenario = scenario(List.of(
                new TestStep.Send(ChannelType.ISO8583, "auth_request_0100",
                        Map.of(), Duration.ofSeconds(5), "auth_response"),
                new TestStep.Assert("field39 == '00'", "Must be approved", true),  // fail_fast
                new TestStep.Assert("field39 == '00'", "Should be skipped", false)
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), UUID.randomUUID());

        assertThat(result.stepExecutions().get(2).result()).isInstanceOf(StepResult.Skipped.class);
    }

    @Test
    void inject_variable_stores_in_context() {
        TestScenario scenario = scenario(List.of(
                new TestStep.InjectVariable("stan", "{{$stan}}"),
                new TestStep.Assert("stan != null", "STAN must be set", false)
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void no_adapter_for_channel_returns_failed() {
        when(iso8583Channel.supports(ChannelType.REST)).thenReturn(false);

        TestScenario scenario = scenario(List.of(
                new TestStep.Send(ChannelType.REST, "GET /api/health",
                        Map.of(), Duration.ofSeconds(5), null)
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.stepExecutions().get(0).result()).isInstanceOf(StepResult.Failed.class);
    }

    @Test
    void loop_serial_runs_all_iterations() throws Exception {
        when(iso8583Channel.execute(any(), any()))
                .thenReturn(new StepResult.Passed("send", Duration.ofMillis(10), Map.of("field39", "00")));

        TestStep inner = new TestStep.Send(ChannelType.ISO8583, "auth_request_0100",
                Map.of(), Duration.ofSeconds(5), null);
        TestScenario scenario = scenario(List.of(
                new TestStep.Loop(3, Duration.ZERO, false, List.of(inner))
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        verify(iso8583Channel, times(3)).execute(any(), any());
    }

    @Test
    void wait_for_human_passes_on_resume() {
        UUID executionId = UUID.randomUUID();
        TestScenario scenario = scenario(List.of(
                new TestStep.WaitForHuman("verify-receipt", "Check receipt", Duration.ofSeconds(5))
        ));

        CompletableFuture<RunExecution.ScenarioExecution> future = CompletableFuture.supplyAsync(
                () -> engine.executeScenario(scenario, new HashMap<>(), executionId));

        // Small delay to ensure the engine is parked on the WaitForHuman step
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        engine.resumeWait(executionId, "verify-receipt", ResumeOutcome.PASS);

        RunExecution.ScenarioExecution result = future.join();
        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void wait_for_human_times_out_and_fails() {
        UUID executionId = UUID.randomUUID();
        TestScenario scenario = scenario(List.of(
                new TestStep.WaitForHuman("verify-receipt", "Check receipt", Duration.ofMillis(100))
        ));

        RunExecution.ScenarioExecution result = engine.executeScenario(scenario, new HashMap<>(), executionId);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.stepExecutions().get(0).result()).isInstanceOf(StepResult.TimedOut.class);
    }

    private TestScenario scenario(List<TestStep> steps) {
        return new TestScenario("test-scenario", "Test Scenario", "",
                "acquiring-service", "payments", "authorization",
                ChannelType.ISO8583, Map.of(), steps, "test.yml", Instant.now());
    }
}
