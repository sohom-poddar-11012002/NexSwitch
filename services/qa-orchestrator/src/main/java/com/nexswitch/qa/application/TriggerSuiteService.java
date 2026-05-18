package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.model.ExecutionStatus;
import com.nexswitch.qa.domain.model.RunExecution;
import com.nexswitch.qa.domain.model.TestSuite;
import com.nexswitch.qa.domain.port.inbound.TriggerRunUseCase;
import com.nexswitch.qa.domain.port.inbound.TriggerSuiteUseCase;
import com.nexswitch.qa.domain.port.outbound.ExecutionEventPublisher;
import com.nexswitch.qa.domain.port.outbound.RunExecutionRepository;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

// LEARN: Suite execution — SEQUENTIAL runs each run in order and applies the OnFailure policy
//        (FAIL_FAST stops immediately, RETRY_ONCE re-runs the failing run once, CONTINUE ignores failures).
//        PARALLEL submits all runs to the virtual-thread executor simultaneously and waits for all
//        futures, collecting pass/fail counts. Both modes poll RunExecutionRepository to detect completion
//        rather than coupling into TriggerRunService internals.
@Service
public class TriggerSuiteService implements TriggerSuiteUseCase {

    private static final Logger log = LoggerFactory.getLogger(TriggerSuiteService.class);
    private static final Duration POLL_INTERVAL  = Duration.ofMillis(500);
    private static final Duration RUN_TIMEOUT    = Duration.ofMinutes(30);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final ScenarioRepository      scenarioRepository;
    private final TriggerRunUseCase       triggerRun;
    private final RunExecutionRepository  executionRepository;
    private final ExecutionEventPublisher eventPublisher;

    public TriggerSuiteService(
            ScenarioRepository scenarioRepository,
            TriggerRunUseCase triggerRun,
            RunExecutionRepository executionRepository,
            ExecutionEventPublisher eventPublisher) {
        this.scenarioRepository = scenarioRepository;
        this.triggerRun         = triggerRun;
        this.executionRepository = executionRepository;
        this.eventPublisher     = eventPublisher;
    }

    @Override
    public UUID trigger(TriggerSuiteCommand command) {
        TestSuite suite = scenarioRepository.findSuiteById(command.suiteId())
                .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + command.suiteId()));
        UUID suiteExecutionId = UUID.randomUUID();
        executor.submit(() -> executeSuite(suite, suiteExecutionId, command.variableOverrides()));
        log.info("qa.suite.triggered suiteId={} suiteExecutionId={}", suite.id(), suiteExecutionId);
        return suiteExecutionId;
    }

    private void executeSuite(TestSuite suite, UUID suiteExecutionId, Map<String, Object> overrides) {
        eventPublisher.publishSuiteStarted(suiteExecutionId, suite.id());

        int passed = 0;
        int failed = 0;
        boolean suitePassed = true;

        if (suite.mode() == TestSuite.ExecutionMode.SEQUENTIAL) {
            for (String runId : suite.runIds()) {
                RunResult result = triggerAndAwait(runId, overrides);
                if (result.passed()) {
                    passed++;
                } else {
                    failed++;
                    if (suite.onFailure() == TestSuite.OnFailure.FAIL_FAST) {
                        suitePassed = false;
                        break;
                    } else if (suite.onFailure() == TestSuite.OnFailure.RETRY_ONCE) {
                        RunResult retry = triggerAndAwait(runId, overrides);
                        if (retry.passed()) { failed--; passed++; }
                        else { suitePassed = false; break; }
                    }
                    // CONTINUE: keep going regardless of failure
                }
            }
            if (failed > 0 && suite.onFailure() != TestSuite.OnFailure.CONTINUE) suitePassed = false;
        } else {
            // LEARN: PARALLEL mode — all runs are submitted simultaneously; futures collected
            //        with CompletableFuture.get() which blocks the virtual thread (cheap).
            List<CompletableFuture<RunResult>> futures = suite.runIds().stream()
                    .map(runId -> CompletableFuture.supplyAsync(
                            () -> triggerAndAwait(runId, overrides), executor))
                    .toList();
            for (CompletableFuture<RunResult> future : futures) {
                try {
                    RunResult result = future.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                    if (result.passed()) passed++; else { failed++; suitePassed = false; }
                } catch (Exception e) {
                    failed++;
                    suitePassed = false;
                    log.warn("qa.suite.run_future_error suiteId={} error={}", suite.id(), e.getMessage());
                }
            }
        }

        ExecutionStatus status = suitePassed ? ExecutionStatus.PASSED : ExecutionStatus.FAILED;
        eventPublisher.publishSuiteComplete(suiteExecutionId, status, passed, failed);
        log.info("qa.suite.complete suiteId={} status={} passed={} failed={}", suite.id(), status, passed, failed);
    }

    private RunResult triggerAndAwait(String runId, Map<String, Object> overrides) {
        UUID executionId = triggerRun.trigger(
                new TriggerRunUseCase.TriggerRunCommand(runId, new HashMap<>(overrides)));
        return new RunResult(executionId, awaitCompletion(executionId));
    }

    // LEARN: Poll-to-completion — virtual thread blocks on sleep() while waiting; OS thread is free.
    //        Simpler than a shared CompletableFuture registry and avoids coupling suite logic
    //        into TriggerRunService internals.
    private boolean awaitCompletion(UUID executionId) {
        long deadlineMs = System.currentTimeMillis() + RUN_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadlineMs) {
            Optional<RunExecution> exec = executionRepository.findById(executionId);
            if (exec.isPresent() && exec.get().status() != ExecutionStatus.RUNNING) {
                return exec.get().status() == ExecutionStatus.PASSED;
            }
            try { Thread.sleep(POLL_INTERVAL.toMillis()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        log.warn("qa.suite.run_timeout executionId={}", executionId);
        return false;
    }

    private record RunResult(UUID executionId, boolean passed) {}
}
