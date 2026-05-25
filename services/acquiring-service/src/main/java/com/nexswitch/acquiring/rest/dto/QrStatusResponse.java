package com.nexswitch.acquiring.rest.dto;

import java.time.Instant;

public record QrStatusResponse(String txnRef, String status, String npciTxnId, Instant expiresAt) {}
