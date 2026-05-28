package com.nexswitch.settlement.service;

import java.math.BigDecimal;

/** Returned by {@link SettlementJobService#runSettlementJob} with job statistics. */
public record SettlementJobResult(
        int settledCount,
        BigDecimal totalAmount,
        String fileName
) {}
