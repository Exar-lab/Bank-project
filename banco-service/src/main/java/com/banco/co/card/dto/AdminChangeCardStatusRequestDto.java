package com.banco.co.card.dto;

import com.banco.co.card.enums.CardStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminChangeCardStatusRequestDto(
        @NotNull(message = "Status is required") CardStatus status,
        @Size(max = 500, message = "Reason must not exceed 500 characters") String reason
) {}
