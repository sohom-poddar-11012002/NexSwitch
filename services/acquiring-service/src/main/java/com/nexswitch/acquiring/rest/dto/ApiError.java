package com.nexswitch.acquiring.rest.dto;

import java.util.List;

public record ApiError(int status, String error, String message, List<Violation> violations) {}
