package com.banco.co.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPinRequestDto(
        @NotBlank(message = "New PIN is required")
        @Size(min = 4, max = 6, message = "PIN must be between 4 and 6 digits")
        String newPin
) {}
