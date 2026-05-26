package com.nexswitch.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// LEARN: Micrometer is a metrics facade (like SLF4J but for metrics) — instrument once, swap
//        backends (Prometheus, Datadog, CloudWatch) via a registry bean. Prometheus scrapes a
//        /actuator/prometheus endpoint; push-based backends (Datadog) use a StepMeterRegistry.
// LEARN: @EnableAspectJAutoProxy activates AspectJ's compile-weaving proxy — Spring wraps any
//        @Aspect bean as a JDK dynamic proxy (interface) or CGLIB proxy (class), allowing
//        cross-cutting concerns (metrics, tracing) without modifying the intercepted class.
@Configuration
@EnableAspectJAutoProxy
public class ObservabilityConfig {
}
