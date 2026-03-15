package com.banco.co.transaction.dto.movement;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequestDto(

        @NotBlank(message = "From account code is required")
        @Size(min = 10, max = 30, message = "Invalid account code format")
        String fromAccountCode,  // ← Cuenta ORIGEN

        @NotBlank(message = "To account code is required")
        @Size(min = 10, max = 30, message = "Invalid account code format")
        String toAccountCode,  // ← Cuenta DESTINO

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        @DecimalMin(value = "0.01", message = "Minimum transfer is 0.01")
        @DecimalMax(value = "99999999999999999.99", message = "Amount exceeds maximum")
        BigDecimal amount,

        @Size(max = 200, message = "Description must not exceed 200 characters")
        String description,  // Opcional

        @AssertTrue(message = "Must confirm transfer")
        Boolean confirmTransfer  // Opcional: confirmación explícita

) {
    public TransferRequestDto {
        // Validar que no sean la misma cuenta
        if (fromAccountCode != null && fromAccountCode.equals(toAccountCode)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        if (amount != null && amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }

        confirmTransfer = confirmTransfer != null ? confirmTransfer : true;
    }
}
