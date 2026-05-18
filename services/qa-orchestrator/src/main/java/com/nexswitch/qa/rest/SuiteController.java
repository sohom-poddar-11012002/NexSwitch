package com.nexswitch.qa.rest;

import com.nexswitch.qa.domain.model.TestSuite;
import com.nexswitch.qa.domain.port.inbound.TriggerSuiteUseCase;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/qa/suites")
public class SuiteController {

    private final TriggerSuiteUseCase triggerSuite;
    private final ScenarioRepository  scenarioRepository;

    public SuiteController(TriggerSuiteUseCase triggerSuite, ScenarioRepository scenarioRepository) {
        this.triggerSuite       = triggerSuite;
        this.scenarioRepository = scenarioRepository;
    }

    @GetMapping
    public List<TestSuite> listSuites() {
        return scenarioRepository.findAllSuites();
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@RequestBody TriggerRequest body) {
        Map<String, Object> overrides = body.variableOverrides() != null ? body.variableOverrides() : Map.of();
        UUID suiteExecutionId = triggerSuite.trigger(
                new TriggerSuiteUseCase.TriggerSuiteCommand(body.suiteId(), overrides));
        return ResponseEntity.accepted().body(Map.of(
                "suiteExecutionId", suiteExecutionId.toString(),
                "status", "RUNNING"));
    }

    public record TriggerRequest(String suiteId, Map<String, Object> variableOverrides) {}
}
