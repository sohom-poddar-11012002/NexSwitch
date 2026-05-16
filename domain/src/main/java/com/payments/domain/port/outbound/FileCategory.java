package com.payments.domain.port.outbound;

// LEARN: DomainEnum — logical file namespace; adapter maps to S3 bucket prefix
public enum FileCategory {
    SETTLEMENT,
    RECONCILIATION_REPORT,
    CHARGEBACK_EVIDENCE,
    PAYOUT_REPORT,
    BANK_STATEMENT,
    TEMP
}
