package com.nexswitch.qa.domain.model;

import java.util.List;
import java.util.Map;

public record TestSuite(
    String id,
    String name,
    List<String> runIds,
    ExecutionMode mode,
    int parallelism,               // max concurrent runs when mode=PARALLEL
    Map<String, String> envProfile // target service URLs for this suite
) {
    public enum ExecutionMode { SEQUENTIAL, PARALLEL }

    public TestSuite {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("suite id must not be blank");
        runIds     = runIds == null ? List.of() : List.copyOf(runIds);
        envProfile = envProfile == null ? Map.of() : Map.copyOf(envProfile);
    }
}
