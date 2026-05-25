package com.nexswitch.acquiring.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitiateRequest(
        @NotBlank @Size(max = 16)  String merchantId,
        @NotBlank @Pattern(regexp = "[\\w.]+@[\\w]+", message = "must be a valid UPI VPA e.g. user@bank")
                                   String payerVpa,
        @NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "must be a decimal with 2 places e.g. 100.00")
                                   String amount,
        @NotBlank @Size(min = 3, max = 3, message = "must be a 3-letter ISO 4217 currency code")
                                   String currency,
        @NotBlank @Size(max = 64)  String orderId,
        Integer expirySeconds
) {}
