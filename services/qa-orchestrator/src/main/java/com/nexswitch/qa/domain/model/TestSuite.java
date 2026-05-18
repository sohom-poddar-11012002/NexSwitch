package com.nexswitch.qa.domain.model;

import java.util.List;
import java.util.Map;

public record TestSuite(
    String id,
    String name,
    List<String> runIds,
    ExecutionMode mode,
    int parallelism,                // max concurrent runs when mode=PARALLEL
    Map<String, String> envProfile, // target service URLs for this suite
    OnFailure onFailure,            // behaviour when a run fails
    String schedule                 // cron expression for scheduled execution; null = manual only
) {
    public enum ExecutionMode { SEQUENTIAL, PARALLEL }

    // LEARN: RETRY_ONCE re-runs the failed run once before marking the suite as FAILED —
    //        catches transient infrastructure blips without requiring full manual re-trigger.
    public enum OnFailure { CONTINUE, FAIL_FAST, RETRY_ONCE }

    public TestSuite {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("suite id must not be blank");
        runIds     = runIds == null ? List.of() : List.copyOf(runIds);
        envProfile = envProfile == null ? Map.of() : Map.copyOf(envProfile);
        onFailure  = onFailure == null ? OnFailure.FAIL_FAST : onFailure;
    }
}
