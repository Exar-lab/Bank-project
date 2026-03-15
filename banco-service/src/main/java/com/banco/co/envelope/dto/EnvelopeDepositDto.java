package com.banco.co.envelope.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record EnvelopeDepositDto(

        @NotBlank(message = "Envelope code is required")
        @Size(min = 10, max = 30, message = "Envelope code must be between 10 and 30 characters")
        String envelopeCode,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Amount format is invalid (max 17 digits, 2 decimals)")
        @DecimalMin(value = "0.01", message = "Minimum deposit amount is 0.01")
        @DecimalMax(value = "99999999999999999.99", message = "Amount exceeds maximum allowed")
        BigDecimal amount,

        @Size(max = 200, message = "Description must not exceed 200 characters")
        String description  // Opcional: descripción del depósito

) {
    // Validación adicional en constructor compacto
    public EnvelopeDepositDto {
        if (amount != null && amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Amount cannot have more than 2 decimal places"
            );
        }
    }
}