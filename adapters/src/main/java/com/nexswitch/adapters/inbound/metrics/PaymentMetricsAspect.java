package com.nexswitch.adapters.inbound.metrics;

import com.nexswitch.domain.model.AuthorizationResult;
import com.nexswitch.domain.port.inbound.AuthorizationCommand;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

// LEARN: AOP metrics aspect — cross-cutting concern in the adapter layer; domain stays pure.
//        Intercepts ProcessPaymentUseCase.execute() and records counters without any domain code change.
//        This is the idiomatic hexagonal pattern: domain defines the port, adapters observe it.
@Aspect
@Component
public class PaymentMetricsAspect {

    private final MeterRegistry registry;

    public PaymentMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* com.nexswitch.domain.port.inbound.ProcessPaymentUseCase.execute(..))")
    public Object recordPaymentMetrics(ProceedingJoinPoint pjp) throws Throwable {
        AuthorizationCommand cmd = (AuthorizationCommand) pjp.getArgs()[0];
        String scheme   = cmd.network().name().toLowerCase();
        String currency = cmd.amount().currency().getCurrencyCode();

        Object raw = pjp.proceed();
        if (!(raw instanceof AuthorizationResult result)) return raw;

        switch (result) {
            case AuthorizationResult.Approved ignored ->
                Counter.builder("payment_authorized_total")
                        .tag("scheme", scheme)
                        .tag("currency", currency)
                        .description("Payments approved by network")
                        .register(registry)
                        .increment();
            case AuthorizationResult.Declined d ->
                Counter.builder("payment_declined_total")
                        .tag("scheme", scheme)
                        .tag("decline_reason", d.reason())
                        .description("Payments declined — issuer or risk")
                        .register(registry)
                        .increment();
            case AuthorizationResult.Blocked b ->
                Counter.builder("fraud_blocked_total")
                        .tag("scheme", scheme)
                        .description("Payments blocked by fraud scoring")
                        .register(registry)
                        .increment();
            case AuthorizationResult.Unknown ignored ->
                Counter.builder("payment_unknown_total")
                        .tag("scheme", scheme)
                        .description("Payments with unknown network outcome")
                        .register(registry)
                        .increment();
        }

        return raw;
    }
}
