package com.nexswitch.qa.rest;

import com.nexswitch.qa.domain.model.TestRun;
import com.nexswitch.qa.domain.model.TestScenario;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
public class ScenarioController {

    private final ScenarioRepository scenarioRepository;

    public ScenarioController(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    @GetMapping("/scenarios")
    public List<TestScenario> listScenarios(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String feature) {
        if (platform != null && project != null && feature != null)
            return scenarioRepository.findScenariosByFeature(platform, project, feature);
        if (platform != null)
            return scenarioRepository.findScenariosByPlatform(platform);
        return scenarioRepository.findAllScenarios();
    }

    @GetMapping("/scenarios/{id}")
    public ResponseEntity<TestScenario> getScenario(@PathVariable String id) {
        return scenarioRepository.findScenarioById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/scenarios/{id}/yaml", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getScenarioYaml(@PathVariable String id) {
        return scenarioRepository.findScenarioYaml(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/run-definitions")
    public List<TestRun> listRuns() {
        return scenarioRepository.findAllRuns();
    }

    @GetMapping("/run-definitions/{id}")
    public ResponseEntity<TestRun> getRun(@PathVariable String id) {
        return scenarioRepository.findRunById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reload")
    public ResponseEntity<Void> reload() {
        scenarioRepository.reload();
        return ResponseEntity.noContent().build();
    }
}
