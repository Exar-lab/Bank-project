package com.banco.co.envelope.dto;

import com.banco.co.envelope.enums.AutoContributeFrequency;
import com.banco.co.envelope.enums.EnvelopeType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnvelopeUpdateDto(

        @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
        @Pattern(
                regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ0-9\\s.,!-]+$",
                message = "Name can contain letters, numbers, spaces and basic punctuation"
        )
        String name,  // Opcional

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,  // Opcional

        EnvelopeType envelopeType,  // Opcional (elimina el duplicado 'type')

        @Positive(message = "Target amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        BigDecimal targetAmount,  // Opcional

        @Future(message = "Target date must be in the future")
        LocalDate targetDate,  // Opcional

        // Auto-contribución
        Boolean autoContribute,  // Opcional

        @Positive(message = "Auto contribute amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        BigDecimal autoContributeAmount,  // Opcional

        AutoContributeFrequency autoContributeFrequency,  // Opcional

        // Round-up
        Boolean roundUpEnabled,  // Opcional

        @Positive(message = "Round up multiple must be positive")
        @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
        BigDecimal roundUpMultiple,  // Opcional

        // Personalization
        @Size(max = 10, message = "Icon must not exceed 10 characters")
        String icon,  // Opcional (emoji)

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color (e.g., #FF5733)")
        String color,  // Opcional (hex color)

        @Min(value = 0, message = "Priority must be between 0 and 10")
        @Max(value = 10, message = "Priority must be between 0 and 10")
        Integer priority  // Opcional

) {
    // Validaciones personalizadas en constructor compacto
    public EnvelopeUpdateDto {
        // Si habilita auto-contribución, debe tener amount y frecuencia
        if (Boolean.TRUE.equals(autoContribute)) {
            if (autoContributeAmount == null) {
                throw new IllegalArgumentException(
                        "Auto contribute amount is required when auto contribute is enabled"
                );
            }
            if (autoContributeFrequency == null) {
                throw new IllegalArgumentException(
                        "Auto contribute frequency is required when auto contribute is enabled"
                );
            }
        }

        // Si habilita round-up, debe especificar múltiplo
        if (Boolean.TRUE.equals(roundUpEnabled) && roundUpMultiple == null) {
            throw new IllegalArgumentException(
                    "Round up multiple is required when round up is enabled"
            );
        }
    }
}