package com.nexswitch.qa.application;

import com.nexswitch.qa.domain.model.*;
import com.nexswitch.qa.domain.port.inbound.TriggerRunUseCase;
import com.nexswitch.qa.domain.port.outbound.*;
import com.nexswitch.qa.domain.service.ScenarioExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// LEARN: Virtual thread executor — each run gets its own virtual thread; the OS thread that
//        handles the HTTP request returns immediately while the run executes asynchronously.
@Service
public class TriggerRunService implements TriggerRunUseCase {

    private static final Logger log = LoggerFactory.getLogger(TriggerRunService.class);

    // LEARN: newVirtualThreadPerTaskExecutor — JDK 21+; zero-cost submit; ideal for I/O-heavy QA runs
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final ScenarioRepository     scenarioRepository;
    private final RunExecutionRepository executionRepository;
    private final ExecutionEventPublisher eventPublisher;
    private final ScenarioExecutionEngine engine;

    public TriggerRunService(
            ScenarioRepository scenarioRepository,
            RunExecutionRepository executionRepository,
            ExecutionEventPublisher eventPublisher,
            ScenarioExecutionEngine engine) {
        this.scenarioRepository  = scenarioRepository;
        this.executionRepository = executionRepository;
        this.eventPublisher      = eventPublisher;
        this.engine              = engine;
    }

    @Override
    public UUID trigger(TriggerRunCommand command) {
        TestRun run = scenarioRepository.findRunById(command.runId())
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + command.runId()));

        UUID executionId = UUID.randomUUID();
        Map<String, Object> sharedContext = new HashMap<>(run.runVariables());
        sharedContext.putAll(command.variableOverrides());

        RunExecution execution = new RunExecution(
                executionId, run.id(), ExecutionStatus.RUNNING,
                List.of(), sharedContext, Instant.now(), null);
        executionRepository.save(execution);

        executor.submit(() -> executeRun(run, execution, sharedContext));
        log.info("qa.run.triggered runId={} executionId={}", run.id(), executionId);
        return executionId;
    }

    private void executeRun(TestRun run, RunExecution initialExecution, Map<String, Object> sharedContext) {
        UUID executionId = initialExecution.id();
        eventPublisher.publishRunStarted(executionId, run.id());

        List<RunExecution.ScenarioExecution> scenarioResults = new ArrayList<>();
        boolean runPassed = true;
        TestRun.SessionConfig session = run.session();
        boolean sessionPoisoned = false; // set when isolate_on_failure=true and a scenario fails

        for (TestRun.RunScenarioRef ref : run.scenarios()) {
            Optional<TestScenario> scenarioOpt = scenarioRepository.findScenarioById(ref.scenarioId());
            if (scenarioOpt.isEmpty()) {
                log.warn("qa.run.scenario_not_found scenarioId={}", ref.scenarioId());
                runPassed = false;
                break;
            }
            TestScenario scenario = scenarioOpt.get();

            // LEARN: STATELESS resets to run-level baseline each scenario — no cross-scenario
            //        state leaks. STATEFUL accumulates captured variables (e.g. auth token from
            //        scenario 1 flows into scenario 2). isolate_on_failure stops carry-over
            //        after a failure so broken state doesn't corrupt downstream assertions.
            Map<String, Object> scenarioCtx = session.mode() == TestRun.SessionMode.STATELESS || sessionPoisoned
                    ? new HashMap<>(run.runVariables())
                    : new HashMap<>(sharedContext);
            scenarioCtx.putAll(ref.variableOverrides());

            RunExecution.ScenarioExecution result = engine.executeScenario(scenario, scenarioCtx, executionId);
            scenarioResults.add(result);

            if (session.mode() == TestRun.SessionMode.STATEFUL && !sessionPoisoned) {
                if (session.carryVariables().isEmpty()) {
                    sharedContext.putAll(scenarioCtx);
                } else {
                    session.carryVariables().forEach(key -> {
                        if (scenarioCtx.containsKey(key)) sharedContext.put(key, scenarioCtx.get(key));
                    });
                }
            }

            if (result.status() != ExecutionStatus.PASSED) {
                runPassed = false;
                if (session.isolateOnFailure()) sessionPoisoned = true;
                break;
            }
        }

        ExecutionStatus finalStatus = runPassed ? ExecutionStatus.PASSED : ExecutionStatus.FAILED;
        RunExecution completed = new RunExecution(
                executionId, run.id(), finalStatus, scenarioResults,
                sharedContext, initialExecution.startedAt(), Instant.now());
        executionRepository.save(completed);
        eventPublisher.publishRunComplete(executionId, finalStatus);
        log.info("qa.run.complete executionId={} status={}", executionId, finalStatus);
    }
}
