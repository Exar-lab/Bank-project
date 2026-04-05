package com.banco.co.card.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdateCardLimitsRequestDto(
        @NotNull(message = "Daily limit is required") @Positive(message = "Daily limit must be positive") BigDecimal dailyLimit,
        @NotNull(message = "Monthly limit is required") @Positive(message = "Monthly limit must be positive") BigDecimal monthlyLimit
) {}
