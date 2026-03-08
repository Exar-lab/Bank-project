package com.banco.co.transaction.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// ServicePaymentRequestDto.java
public record ServicePaymentRequestDto(
        @NotBlank String accountCode,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String serviceProvider,  // "ICE", "AyA", etc.
        @NotBlank String referenceNumber,
        String description
) {}
