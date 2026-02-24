package com.banco.co.account.dto;

import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponseDto(
        UUID id,
        String accountCode,
        String accountNumber,
        AccountType accountType,
        AccountStatus status,
        String currency,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal overdraftLimit,
        BigDecimal interestRate,
        UUID userId,
        UUID bankId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastTransactionAt) {
}
