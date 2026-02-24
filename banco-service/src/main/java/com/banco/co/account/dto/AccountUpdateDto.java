package com.banco.co.account.dto;

import com.banco.co.account.enums.AccountStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountUpdateDto(

        @DecimalMin(value = "0", message = "Overdraft limit must be zero or positive") BigDecimal overdraftLimit,

        @DecimalMin(value = "0", message = "Interest rate must be zero or positive") @DecimalMax(value = "100", message = "Interest rate must not exceed 100") BigDecimal interestRate,

        AccountStatus status,

        @Size(max = 3, message = "Currency must not exceed 3 characters") @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO 4217 code (e.g., CRC, USD)") String currency) {
}
