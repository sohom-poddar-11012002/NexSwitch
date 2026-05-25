package com.nexswitch.acquiring.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OutcomeRequest(
        @NotBlank @Size(max = 19)  String collectId,
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED", message = "must be APPROVED or REJECTED")
                                   String status,
        @NotBlank @Size(max = 35)  String npciTxnId
) {}
