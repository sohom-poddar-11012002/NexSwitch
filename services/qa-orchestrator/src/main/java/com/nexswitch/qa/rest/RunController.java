package com.nexswitch.qa.rest;

import com.nexswitch.qa.domain.model.ResumeOutcome;
import com.nexswitch.qa.domain.model.RunExecution;
import com.nexswitch.qa.domain.port.inbound.ResumeStepUseCase;
import com.nexswitch.qa.domain.port.inbound.TriggerRunUseCase;
import com.nexswitch.qa.domain.port.outbound.RunExecutionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/qa/runs")
public class RunController {

    private final TriggerRunUseCase     triggerRun;
    private final ResumeStepUseCase     resumeStep;
    private final RunExecutionRepository executionRepository;

    public RunController(TriggerRunUseCase triggerRun,
                         ResumeStepUseCase resumeStep,
                         RunExecutionRepository executionRepository) {
        this.triggerRun          = triggerRun;
        this.resumeStep          = resumeStep;
        this.executionRepository = executionRepository;
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@RequestBody TriggerRequest body) {
        // Merge envProfile (base URLs etc.) into variableOverrides so adapters can read them from context
        Map<String, Object> merged = new java.util.HashMap<>(body.variableOverrides() != null ? body.variableOverrides() : Map.of());
        if (body.envProfile() != null) merged.putAll(body.envProfile());
        TriggerRunUseCase.TriggerRunCommand command = new TriggerRunUseCase.TriggerRunCommand(
                body.runId(), merged);
        UUID executionId = triggerRun.trigger(command);
        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId.toString(),
                "status", "RUNNING"));
    }

    @PostMapping("/{executionId}/steps/{stepId}/resume")
    public ResponseEntity<Void> resume(
            @PathVariable UUID executionId,
            @PathVariable String stepId,
            @RequestBody ResumeRequest body) {
        ResumeOutcome outcome = ResumeOutcome.valueOf(body.outcome().toUpperCase());
        resumeStep.resume(executionId, stepId, outcome);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<RunExecution> recent(@RequestParam(defaultValue = "20") int limit) {
        return executionRepository.findRecentExecutions(limit);
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<RunExecution> get(@PathVariable UUID executionId) {
        return executionRepository.findById(executionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record TriggerRequest(String runId, Map<String, Object> variableOverrides, Map<String, Object> envProfile) {}
    public record ResumeRequest(String outcome) {}
}
