package com.nexswitch.domain.model;

// LEARN: ValueObject — pre-computed Redis counters; separates data fetch from rule evaluation
public record FraudVelocityData(
        int panTransactionsLast5Min,
        int panTransactionsLastHour,
        int terminalTransactionsLastHour,
        boolean isFirstTransactionOnPan,
        boolean isImpossibleTravel
) {
    public FraudVelocityData {
        if (panTransactionsLast5Min < 0)
            throw new IllegalArgumentException("panTransactionsLast5Min must not be negative");
        if (panTransactionsLastHour < 0)
            throw new IllegalArgumentException("panTransactionsLastHour must not be negative");
        if (terminalTransactionsLastHour < 0)
            throw new IllegalArgumentException("terminalTransactionsLastHour must not be negative");
    }
}
