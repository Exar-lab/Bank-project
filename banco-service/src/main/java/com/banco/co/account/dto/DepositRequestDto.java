package com.banco.co.account.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record DepositRequestDto(

        String accountCode,
        @DecimalMin(value = "1", message = "Amount must be greater than 0")
        BigDecimal amount
) {
}
