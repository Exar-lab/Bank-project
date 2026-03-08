package com.banco.co.transaction.dto.movement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// CashDepositRequestDto.java
public record CashDepositRequestDto(
        @NotBlank String accountCode,
        @NotNull @Positive BigDecimal amount,
        String customerName,  // Para verificación
        String description
) {}
