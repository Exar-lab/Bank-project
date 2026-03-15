package com.banco.co.transaction.dto;

import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponseDto(

        // Identificación
        String transactionCode,
        TransactionType type,  // DEPOSIT, WITHDRAWAL, TRANSFER
        TransactionStatus status,  // PENDING, COMPLETED, FAILED, REVERSED

        // Cuentas involucradas
        String fromAccountCode,  // null para DEPOSIT
        String toAccountCode,    // null para WITHDRAWAL

        // Montos
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal netAmount,
        String currency,

        // Balances ANTES y DESPUÉS
        BigDecimal fromAccountBalanceBefore,
        BigDecimal fromAccountBalanceAfter,
        BigDecimal toAccountBalanceBefore,
        BigDecimal toAccountBalanceAfter,

        // Metadata
        String description,
        LocalDateTime createdAt,
        LocalDateTime completedAt,

        // Resultado
        String confirmationMessage  // "Depósito exitoso. Nuevo balance: ₡60,000"

) {}
