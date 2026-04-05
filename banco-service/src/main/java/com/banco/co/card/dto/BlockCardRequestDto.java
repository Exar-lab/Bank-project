package com.banco.co.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockCardRequestDto(
        @NotBlank(message = "Block reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {}
