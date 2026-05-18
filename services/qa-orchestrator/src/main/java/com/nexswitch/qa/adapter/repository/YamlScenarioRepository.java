package com.nexswitch.qa.adapter.repository;

import com.nexswitch.qa.domain.model.*;
import com.nexswitch.qa.domain.port.outbound.ScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// LEARN: PathMatchingResourcePatternResolver — Spring's classpath scanner that resolves
//        wildcard patterns like "classpath*:scenarios/**/*.yml" across all JARs on the classpath.
@Component
public class YamlScenarioRepository implements ScenarioRepository {

    private static final Logger log = LoggerFactory.getLogger(YamlScenarioRepository.class);

    private final Map<String, TestScenario> scenarios = new ConcurrentHashMap<>();
    private final Map<String, TestRun>      runs      = new ConcurrentHashMap<>();
    private final Map<String, TestSuite>    suites    = new ConcurrentHashMap<>();

    private final String scenarioPattern;
    private final String runPattern;
    private final String suitePattern;

    public YamlScenarioRepository(
            @Value("${qa.scenarios.pattern:classpath*:scenarios/**/*.yml}") String scenarioPattern,
            @Value("${qa.runs.pattern:classpath*:runs/**/*.yml}") String runPattern,
            @Value("${qa.suites.pattern:classpath*:suites/**/*.yml}") String suitePattern) {
        this.scenarioPattern = scenarioPattern;
        this.runPattern      = runPattern;
        this.suitePattern    = suitePattern;
        reload();
    }

    @Override
    public List<TestScenario> findAllScenarios() {
        return List.copyOf(scenarios.values());
    }

    @Override
    public Optional<TestScenario> findScenarioById(String id) {
        return Optional.ofNullable(scenarios.get(id));
    }

    @Override
    public List<TestScenario> findScenariosByPlatform(String platform) {
        return scenarios.values().stream()
                .filter(s -> platform.equalsIgnoreCase(s.platform()))
                .collect(Collectors.toList());
    }

    @Override
    public List<TestScenario> findScenariosByFeature(String platform, String project, String feature) {
        return scenarios.values().stream()
                .filter(s -> platform.equalsIgnoreCase(s.platform())
                          && project.equalsIgnoreCase(s.project())
                          && feature.equalsIgnoreCase(s.feature()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TestRun> findRunById(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<TestRun> findAllRuns() {
        return List.copyOf(runs.values());
    }

    @Override
    public Optional<TestSuite> findSuiteById(String id) {
        return Optional.ofNullable(suites.get(id));
    }

    @Override
    public List<TestSuite> findAllSuites() {
        return List.copyOf(suites.values());
    }

    @Override
    public void reload() {
        scenarios.clear();
        runs.clear();
        suites.clear();
        loadResources(scenarioPattern, "scenario");
        loadResources(runPattern, "run");
        loadResources(suitePattern, "suite");
        log.info("qa.yaml.loaded scenarios={} runs={} suites={}", scenarios.size(), runs.size(), suites.size());
    }

    @SuppressWarnings("unchecked")
    private void loadResources(String pattern, String type) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> root = new Yaml().load(in);
                    if (root == null) continue;
                    switch (type) {
                        case "scenario" -> parseScenario(root, resource.getFilename()).ifPresent(s -> scenarios.put(s.id(), s));
                        case "run"      -> parseRun(root).ifPresent(r -> runs.put(r.id(), r));
                        case "suite"    -> parseSuite(root).ifPresent(s -> suites.put(s.id(), s));
                    }
                } catch (Exception e) {
                    log.warn("qa.yaml.parse_failed file={} type={} error={}", resource.getFilename(), type, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("qa.yaml.scan_failed pattern={} error={}", pattern, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<TestScenario> parseScenario(Map<String, Object> root, String filename) {
        Map<String, Object> s = (Map<String, Object>) root.get("scenario");
        if (s == null) return Optional.empty();

        String id          = (String) s.get("id");
        String name        = (String) s.getOrDefault("name", id);
        String description = (String) s.getOrDefault("description", "");
        String platform    = (String) s.getOrDefault("platform", "unknown");
        String project     = (String) s.getOrDefault("project", "unknown");
        String feature     = (String) s.getOrDefault("feature", "unknown");
        String channelStr  = (String) s.getOrDefault("channel", "ISO8583");
        ChannelType channel = ChannelType.valueOf(channelStr.toUpperCase());

        Map<String, Object> vars = (Map<String, Object>) s.getOrDefault("variables", Map.of());
        List<Map<String, Object>> stepMaps = (List<Map<String, Object>>) s.getOrDefault("steps", List.of());
        List<TestStep> steps = stepMaps.stream()
                .map(this::parseStep)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (id == null || steps.isEmpty()) return Optional.empty();
        return Optional.of(new TestScenario(id, name, description, platform, project, feature,
                channel, new HashMap<>(vars), steps, filename, Instant.now()));
    }

    @SuppressWarnings("unchecked")
    private Optional<TestRun> parseRun(Map<String, Object> root) {
        Map<String, Object> r = (Map<String, Object>) root.get("run");
        if (r == null) return Optional.empty();

        String id   = (String) r.get("id");
        String name = (String) r.getOrDefault("name", id);
        Map<String, Object> runVars = (Map<String, Object>) r.getOrDefault("variables", Map.of());

        // Parse session config
        Map<String, Object> sessionMap = (Map<String, Object>) r.getOrDefault("session", Map.of());
        String sessionModeStr = (String) sessionMap.getOrDefault("mode", "STATEFUL");
        TestRun.SessionMode sessionMode = TestRun.SessionMode.valueOf(sessionModeStr.toUpperCase());
        List<String> carryVars = (List<String>) sessionMap.getOrDefault("carry_variables", List.of());
        boolean isolateOnFailure = Boolean.TRUE.equals(sessionMap.getOrDefault("isolate_on_failure", true));
        TestRun.SessionConfig session = new TestRun.SessionConfig(sessionMode, carryVars, isolateOnFailure);

        List<Map<String, Object>> scenarioRefs = (List<Map<String, Object>>) r.getOrDefault("scenarios", List.of());
        List<TestRun.RunScenarioRef> refs = scenarioRefs.stream()
                .map(ref -> new TestRun.RunScenarioRef(
                        (String) ref.get("id"),
                        (Map<String, Object>) ref.getOrDefault("variable_overrides", Map.of())))
                .collect(Collectors.toList());

        if (id == null || refs.isEmpty()) return Optional.empty();
        return Optional.of(new TestRun(id, name, refs, new HashMap<>(runVars), session));
    }

    @SuppressWarnings("unchecked")
    private Optional<TestSuite> parseSuite(Map<String, Object> root) {
        Map<String, Object> s = (Map<String, Object>) root.get("suite");
        if (s == null) return Optional.empty();

        String id           = (String) s.get("id");
        String name         = (String) s.getOrDefault("name", id);
        List<String> runIds = (List<String>) s.getOrDefault("run_ids", List.of());
        String modeStr      = (String) s.getOrDefault("mode", "SEQUENTIAL");
        int parallelism     = ((Number) s.getOrDefault("parallelism", 1)).intValue();
        Map<String, String> envProfile = (Map<String, String>) s.getOrDefault("env_profile", Map.of());
        String onFailureStr = (String) s.getOrDefault("on_failure", "FAIL_FAST");
        String schedule     = (String) s.get("schedule");

        if (id == null || runIds.isEmpty()) return Optional.empty();
        TestSuite.ExecutionMode mode = TestSuite.ExecutionMode.valueOf(modeStr.toUpperCase());
        TestSuite.OnFailure onFailure = TestSuite.OnFailure.valueOf(onFailureStr.toUpperCase());
        return Optional.of(new TestSuite(id, name, runIds, mode, parallelism,
                new HashMap<>(envProfile), onFailure, schedule));
    }

    @SuppressWarnings("unchecked")
    private TestStep parseStep(Map<String, Object> m) {
        String type = (String) m.get("type");
        if (type == null) return null;
        return switch (type) {
            case "send" -> {
                String channelStr  = (String) m.getOrDefault("channel", "ISO8583");
                String operation   = (String) m.getOrDefault("operation", "");
                Map<String, Object> payload = (Map<String, Object>) m.getOrDefault("payload", Map.of());
                long timeoutMs     = ((Number) m.getOrDefault("timeout_ms", 15000)).longValue();
                String captureAs   = (String) m.get("capture_response_as");
                yield new TestStep.Send(ChannelType.valueOf(channelStr.toUpperCase()),
                        operation, new HashMap<>(payload), Duration.ofMillis(timeoutMs), captureAs);
            }
            case "assert" -> {
                String expr  = (String) m.getOrDefault("expression", "");
                String desc  = (String) m.getOrDefault("description", expr);
                boolean fast = Boolean.TRUE.equals(m.get("fail_fast"));
                yield new TestStep.Assert(expr, desc, fast);
            }
            case "wait_for_human" -> {
                String stepId      = (String) m.getOrDefault("step_id", "human_" + UUID.randomUUID());
                String instruction = (String) m.getOrDefault("instruction", "");
                long timeoutMs     = ((Number) m.getOrDefault("timeout_ms", 120000)).longValue();
                yield new TestStep.WaitForHuman(stepId, instruction, Duration.ofMillis(timeoutMs));
            }
            case "loop" -> {
                int count        = ((Number) m.getOrDefault("count", 1)).intValue();
                long delayMs     = ((Number) m.getOrDefault("delay_between_ms", 0)).longValue();
                boolean parallel = Boolean.TRUE.equals(m.get("parallel"));
                List<Map<String, Object>> innerMaps = (List<Map<String, Object>>) m.getOrDefault("steps", List.of());
                List<TestStep> inner = innerMaps.stream().map(this::parseStep).filter(Objects::nonNull).collect(Collectors.toList());
                yield new TestStep.Loop(count, Duration.ofMillis(delayMs), parallel, inner);
            }
            case "inject_variable" -> {
                String varName = (String) m.get("name");
                String expr    = (String) m.getOrDefault("value", "");
                yield new TestStep.InjectVariable(varName, expr);
            }
            default -> {
                log.warn("qa.yaml.unknown_step_type type={}", type);
                yield null;
            }
        };
    }
}
