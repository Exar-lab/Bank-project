package com.banco.co.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record WithdrawalRequestDto(

        @NotBlank(message = "Account code is required")
        @Size(min = 10, max = 30, message = "Invalid account code format")
        String accountCode,  // ← Cuenta ORIGEN (de donde sale el dinero)

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        @DecimalMin(value = "0.01", message = "Minimum withdrawal is 0.01")
        @DecimalMax(value = "99999999999999999.99", message = "Amount exceeds maximum")
        BigDecimal amount,

        @Size(max = 200, message = "Description must not exceed 200 characters")
        String description,  // Opcional

        @AssertTrue(message = "Must confirm withdrawal")
        Boolean confirmWithdrawal  // Opcional: confirmación explícita

) {
    public WithdrawalRequestDto {
        if (amount != null && amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }

        confirmWithdrawal = confirmWithdrawal != null ? confirmWithdrawal : true;
    }
}
