package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.model.ExecutionStatus;
import com.nexswitch.qa.domain.model.RunExecution;
import com.nexswitch.qa.domain.model.TestSuite;
import com.nexswitch.qa.domain.port.inbound.TriggerRunUseCase;
import com.nexswitch.qa.domain.port.inbound.TriggerSuiteUseCase;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import com.nexswitch.qa.domain.port.outbound.RunExecutionRepository;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerSuiteServiceTest {

    @Mock ScenarioRepository      scenarioRepository;
    @Mock TriggerRunUseCase       triggerRun;
    @Mock RunExecutionRepository  executionRepository;
    @Mock ExecutionEventPublisher eventPublisher;

    TriggerSuiteService service;

    @BeforeEach
    void setUp() {
        service = new TriggerSuiteService(scenarioRepository, triggerRun, executionRepository, eventPublisher);
    }

    @Test
    void sequentialAllPass_publishesSuitePassed() throws Exception {
        TestSuite suite = suite("s1", TestSuite.ExecutionMode.SEQUENTIAL, TestSuite.OnFailure.FAIL_FAST,
                List.of("run-a", "run-b"));
        when(scenarioRepository.findSuiteById("s1")).thenReturn(Optional.of(suite));

        UUID execA = UUID.randomUUID();
        UUID execB = UUID.randomUUID();
        when(triggerRun.trigger(any())).thenReturn(execA, execB);
        when(executionRepository.findById(execA)).thenReturn(Optional.of(passedExecution(execA)));
        when(executionRepository.findById(execB)).thenReturn(Optional.of(passedExecution(execB)));

        UUID suiteExecId = service.trigger(new TriggerSuiteUseCase.TriggerSuiteCommand("s1", Map.of()));

        // allow virtual thread to complete
        Thread.sleep(200);

        verify(eventPublisher).publishSuiteStarted(suiteExecId, "s1");
        verify(eventPublisher).publishSuiteComplete(suiteExecId, ExecutionStatus.PASSED, 2, 0);
    }

    @Test
    void sequentialFailFast_stopsOnFirstFailure() throws Exception {
        TestSuite suite = suite("s2", TestSuite.ExecutionMode.SEQUENTIAL, TestSuite.OnFailure.FAIL_FAST,
                List.of("run-a", "run-b", "run-c"));
        when(scenarioRepository.findSuiteById("s2")).thenReturn(Optional.of(suite));

        UUID execA = UUID.randomUUID();
        when(triggerRun.trigger(any())).thenReturn(execA);
        when(executionRepository.findById(execA)).thenReturn(Optional.of(failedExecution(execA)));

        UUID suiteExecId = service.trigger(new TriggerSuiteUseCase.TriggerSuiteCommand("s2", Map.of()));
        Thread.sleep(200);

        // Only 1 trigger call — FAIL_FAST stops after first failure
        verify(triggerRun, times(1)).trigger(any());
        verify(eventPublisher).publishSuiteComplete(suiteExecId, ExecutionStatus.FAILED, 0, 1);
    }

    @Test
    void sequentialContinue_runsAllDespiteFailure() throws Exception {
        TestSuite suite = suite("s3", TestSuite.ExecutionMode.SEQUENTIAL, TestSuite.OnFailure.CONTINUE,
                List.of("run-a", "run-b"));
        when(scenarioRepository.findSuiteById("s3")).thenReturn(Optional.of(suite));

        UUID execA = UUID.randomUUID();
        UUID execB = UUID.randomUUID();
        when(triggerRun.trigger(any())).thenReturn(execA, execB);
        when(executionRepository.findById(execA)).thenReturn(Optional.of(failedExecution(execA)));
        when(executionRepository.findById(execB)).thenReturn(Optional.of(passedExecution(execB)));

        UUID suiteExecId = service.trigger(new TriggerSuiteUseCase.TriggerSuiteCommand("s3", Map.of()));
        Thread.sleep(300);

        verify(triggerRun, times(2)).trigger(any()); // both runs triggered
        verify(eventPublisher).publishSuiteComplete(eq(suiteExecId), any(), anyInt(), anyInt());
    }

    @Test
    void parallelMode_triggersAllRunsSimultaneously() throws Exception {
        TestSuite suite = suite("s4", TestSuite.ExecutionMode.PARALLEL, TestSuite.OnFailure.CONTINUE,
                List.of("run-a", "run-b", "run-c"));
        when(scenarioRepository.findSuiteById("s4")).thenReturn(Optional.of(suite));

        UUID execA = UUID.randomUUID();
        UUID execB = UUID.randomUUID();
        UUID execC = UUID.randomUUID();
        when(triggerRun.trigger(any())).thenReturn(execA, execB, execC);
        when(executionRepository.findById(execA)).thenReturn(Optional.of(passedExecution(execA)));
        when(executionRepository.findById(execB)).thenReturn(Optional.of(passedExecution(execB)));
        when(executionRepository.findById(execC)).thenReturn(Optional.of(passedExecution(execC)));

        service.trigger(new TriggerSuiteUseCase.TriggerSuiteCommand("s4", Map.of()));
        Thread.sleep(400);

        verify(triggerRun, times(3)).trigger(any());
    }

    @Test
    void suiteNotFound_throwsIllegalArgument() {
        when(scenarioRepository.findSuiteById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.trigger(
                new TriggerSuiteUseCase.TriggerSuiteCommand("missing", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Suite not found");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private TestSuite suite(String id, TestSuite.ExecutionMode mode, TestSuite.OnFailure onFailure,
                             List<String> runIds) {
        return new TestSuite(id, id + "-name", runIds, mode, 1, Map.of(), onFailure, null);
    }

    private RunExecution passedExecution(UUID id) {
        return new RunExecution(id, "run", ExecutionStatus.PASSED, List.of(), Map.of(), Instant.now(), Instant.now());
    }

    private RunExecution failedExecution(UUID id) {
        return new RunExecution(id, "run", ExecutionStatus.FAILED, List.of(), Map.of(), Instant.now(), Instant.now());
    }
}
