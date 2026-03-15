package com.banco.co.transaction.dto.movement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// CheckDepositRequestDto.java
public record CheckDepositRequestDto(
        @NotBlank String accountCode,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String checkNumber,
        @NotBlank String bankName,
        String description
) {}