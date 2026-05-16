package com.nexswitch.domain.model;

// LEARN: DomainEnum — BLOCK level triggers rejection before network round-trip; saves latency on hot path
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    BLOCK
}
