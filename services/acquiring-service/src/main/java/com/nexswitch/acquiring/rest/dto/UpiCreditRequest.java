package com.nexswitch.acquiring.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpiCreditRequest(
        @NotBlank @Size(max = 35)   String npciTxnId,
        @NotBlank @Pattern(regexp = "[\\w.]+@[\\w]+", message = "must be a valid UPI VPA e.g. user@bank")
                                    String payerVpa,
        @NotBlank @Pattern(regexp = "[\\w.]+@[\\w]+", message = "must be a valid UPI VPA e.g. user@bank")
                                    String payeeVpa,
        @NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "must be a decimal with 2 places e.g. 100.00")
                                    String amount,
        @NotBlank                   String txnRef
) {}
