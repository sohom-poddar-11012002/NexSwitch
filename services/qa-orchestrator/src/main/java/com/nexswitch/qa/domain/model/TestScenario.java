package com.nexswitch.qa.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// LEARN: Immutable record loaded from YAML — the YAML file is source of truth in git;
//        this record is the parsed, validated in-memory representation.
//        Taxonomy: platform → project → feature → scenario (e.g. acquiring-service/payments/authorization).
public record TestScenario(
    String id,
    String name,
    String description,
    String platform,           // e.g. "acquiring-service", "ops-portal", "merchant-dashboard"
    String project,            // e.g. "payments", "onboarding"
    String feature,            // e.g. "authorization", "boundary", "security"
    ChannelType primaryChannel,
    Map<String, Object> variables,
    List<TestStep> steps,
    String yamlPath,           // classpath path — for traceability in reports
    Instant loadedAt
) {
    public TestScenario {
        if (id == null || id.isBlank())       throw new IllegalArgumentException("scenario id must not be blank");
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("scenario must have at least one step");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        steps     = List.copyOf(steps);
    }
}
