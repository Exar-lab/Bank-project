package com.banco.co.notification.email.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountOperationEmailContext(
        String recipientName,
        String operationType,
        String operationLabel,
        BigDecimal amount,
        String currency,
        String accountCode,
        String transactionCode,
        LocalDateTime occurredAt,
        String bankName
) {}
