package com.payments.domain.model;

// LEARN: ValueObject — BIN prefix matching encapsulated here; matches() keeps routing logic out of RoutingEngine
public record RoutingRule(String binPrefix, PaymentNetwork network, int priority) {

    public RoutingRule {
        if (binPrefix == null || binPrefix.isBlank())
            throw new IllegalArgumentException("binPrefix must not be blank");
        if (network == null)
            throw new IllegalArgumentException("network must not be null");
        if (priority < 0)
            throw new IllegalArgumentException("priority must not be negative");
    }

    public boolean matches(String pan) {
        return pan != null && pan.startsWith(binPrefix);
    }
}
