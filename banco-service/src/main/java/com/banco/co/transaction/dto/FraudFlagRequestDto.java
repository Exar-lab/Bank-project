package com.banco.co.transaction.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FraudFlagRequestDto(
        @NotNull
        @DecimalMin("0.0") @DecimalMax("1.0")
        BigDecimal fraudScore,

        @NotBlank
        String reason
) {}
