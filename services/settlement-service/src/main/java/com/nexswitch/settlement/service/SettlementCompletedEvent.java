package com.nexswitch.settlement.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Published via {@link org.springframework.context.ApplicationEventPublisher} after a successful
 * settlement run. Listeners (e.g. notification service) react asynchronously.
 */
public record SettlementCompletedEvent(
        LocalDate settlementDate,
        int settledCount,
        BigDecimal totalAmount,
        String fileName
) {}
