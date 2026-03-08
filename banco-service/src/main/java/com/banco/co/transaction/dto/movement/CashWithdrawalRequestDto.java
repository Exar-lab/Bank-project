package com.banco.co.transaction.dto.movement;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// CashWithdrawalRequestDto.java
public record CashWithdrawalRequestDto(
        @NotBlank String accountCode,
        @NotNull @Positive BigDecimal amount,
        String customerName,  // Para verificación
        @AssertTrue Boolean customerIdVerified,  // Empleado verificó cédula
        String description
) {}
