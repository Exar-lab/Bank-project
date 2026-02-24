package com.banco.co.account.dto;

import com.banco.co.account.enums.AccountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountRequestDto(

        @NotNull(message = "Account type is required") AccountType accountType,

        @NotBlank(message = "Currency is required") @Size(max = 3, message = "Currency must not exceed 3 characters") @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO 4217 code (e.g., CRC, USD)") String currency,

        @DecimalMin(value = "0", message = "Overdraft limit must be zero or positive") BigDecimal overdraftLimit,

        @DecimalMin(value = "0", message = "Interest rate must be zero or positive") @DecimalMax(value = "100", message = "Interest rate must not exceed 100") BigDecimal interestRate,

        @NotBlank(message = "Document number is required") @Size(max = 12, message = "Document number must not exceed 12 characters") @Pattern(regexp = "^[0-9]+$", message = "Document number must contain only numbers") String documentNumber
) {
}
