package com.nexswitch.acquiring.rest.dto;

import java.time.Instant;

public record GenerateResponse(String txnRef, String qrImageBase64, Instant expiresAt) {}
