package com.nexswitch.acquiring.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenerateRequest(
        @NotBlank @Size(max = 16)  String merchantId,
        @NotBlank @Size(max = 16)  String terminalId,
        @NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "must be a decimal with 2 places e.g. 100.00")
                                   String amount,
        @NotBlank @Size(min = 3, max = 3, message = "must be a 3-letter ISO 4217 currency code")
                                   String currency,
        @NotBlank @Size(max = 64)  String orderId
) {}
