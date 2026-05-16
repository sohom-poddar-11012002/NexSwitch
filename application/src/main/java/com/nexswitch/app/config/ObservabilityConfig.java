package com.nexswitch.app.config;

import org.springframework.context.annotation.Configuration;

// LEARN: Micrometer is a metrics facade (like SLF4J but for metrics) — instrument once, swap
//        backends (Prometheus, Datadog, CloudWatch) via a registry bean. Prometheus scrapes a
//        /actuator/prometheus endpoint; push-based backends (Datadog) use a StepMeterRegistry.
/** Shared ObservabilityConfig — populated as infrastructure is wired in Sprint Week 1. */
@Configuration
public class ObservabilityConfig {
}
