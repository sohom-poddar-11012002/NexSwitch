package com.nexswitch.acquiring.rest.dto;

import java.time.Instant;

public record InitiateResponse(String collectId, Instant expiresAt) {}
