package com.nexswitch.settlement.service;

import java.math.BigDecimal;

/** Returned by {@link PayoutJobService#runPayoutJob} with payout statistics. */
public record PayoutJobResult(int merchantCount, BigDecimal totalPayout) {}
