package com.banco.co.envelope.dto;

import com.banco.co.envelope.enums.AutoContributeFrequency;
import com.banco.co.envelope.enums.EnvelopeType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnvelopeRequestDto(

        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
        @Pattern(
                regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ0-9\\s.,!-]+$",
                message = "Name can contain letters, numbers, spaces and basic punctuation"
        )
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,  // Opcional

        @NotNull(message = "Account code is required")
        @Pattern(
                regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ0-9\\s.,!-]+$",
                message = "account code can contain letters, numbers, spaces and basic punctuation"
        )
        String accountCode,

        @NotNull(message = "Envelope type is required")
        EnvelopeType envelopeType,

        @NotNull(message = "Target amount is required")
        @Positive(message = "Target amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        BigDecimal targetAmount,

        @Future(message = "Target date must be in the future")
        LocalDate targetDate,  // Opcional

        // Auto-contribución
        Boolean autoContribute,  // Default false

        @Positive(message = "Auto contribute amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        BigDecimal autoContributeAmount,

        AutoContributeFrequency autoContributeFrequency,  // DAILY, WEEKLY, MONTHLY

        // Round-up (redondeo de transacciones)
        Boolean roundUpEnabled,  // Default false

        @Positive(message = "Round up multiple must be positive")
        BigDecimal roundUpMultiple,  // Ejemplo: 1000 (redondear a múltiplo de 1000)

        // Personalization
        @Size(max = 10, message = "Icon must not exceed 10 characters")
        String icon,  // Emoji

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color")
        String color,  // Hex color

        @Min(0)
        @Max(10)
        Integer priority  // 0-10, para ordenar

) {

        // Constructor compacto con validaciones adicionales
        public EnvelopeRequestDto {
                // Si habilita auto-contribución, debe especificar amount y frecuencia
                if (Boolean.TRUE.equals(autoContribute)) {
                        if (autoContributeAmount == null) {
                                throw new IllegalArgumentException("Auto contribute amount is required when auto contribute is enabled");
                        }
                        if (autoContributeFrequency == null) {
                                throw new IllegalArgumentException("Auto contribute frequency is required when auto contribute is enabled");
                        }
                }

                // Si habilita round-up, debe especificar múltiplo
                if (Boolean.TRUE.equals(roundUpEnabled) && roundUpMultiple == null) {
                        throw new IllegalArgumentException("Round up multiple is required when round up is enabled");
                }

                // Defaults
                autoContribute = autoContribute != null ? autoContribute : false;
                roundUpEnabled = roundUpEnabled != null ? roundUpEnabled : false;
                priority = priority != null ? priority : 5;
        }
}