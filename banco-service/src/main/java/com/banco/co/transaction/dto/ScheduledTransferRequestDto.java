package com.banco.co.transaction.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ScheduledTransferRequestDto.java
public record ScheduledTransferRequestDto(
        @NotBlank String fromAccountCode,
        @NotBlank String toAccountCode,
        @NotNull @Positive BigDecimal amount,
        @NotNull @Future LocalDateTime scheduledFor,
        String description
) {}
