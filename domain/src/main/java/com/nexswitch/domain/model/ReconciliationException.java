package com.nexswitch.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

// LEARN: Three exception categories cover the full three-way diff (switch vs network vs bank):
//        MISSING_IN_NETWORK = we see the txn, bank file doesn't — needs investigation/chargeback
//        MISSING_IN_SWITCH  = bank file has it, we don't — possible duplicate charge or lost txn
//        AMOUNT_MISMATCH    = both sides see it but amounts differ — interchange calculation error
public record ReconciliationException(
        UUID runId,
        ExceptionCategory category,
        Severity severity,
        UUID transactionId,     // null for MISSING_IN_SWITCH
        String rrn,
        BigDecimal ourAmount,   // null for MISSING_IN_SWITCH
        BigDecimal networkAmount // null for MISSING_IN_NETWORK
) {
    public enum ExceptionCategory {
        MISSING_IN_NETWORK, MISSING_IN_SWITCH, AMOUNT_MISMATCH
    }

    public enum Severity { CRIT, HIGH, MED, INFO }
}
