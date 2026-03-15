package com.banco.co.envelope.dto;

import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnvelopeResponseDto(
        String name,
        EnvelopeType type,
        EnvelopeStatus status,
        LocalDate targetDate,
        BigDecimal progressPercentage,
        BigDecimal balance,
        String icon,
        String color
) {
}
