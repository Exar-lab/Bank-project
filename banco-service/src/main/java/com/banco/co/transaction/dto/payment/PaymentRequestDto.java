package com.banco.co.transaction.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// PaymentRequestDto.java
public record PaymentRequestDto(
        @NotBlank String cardCode,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String merchantName,
        String merchantMccCode,
        String description
) {}
