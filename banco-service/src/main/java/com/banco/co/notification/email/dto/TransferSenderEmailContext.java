package com.banco.co.notification.email.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferSenderEmailContext(
        String recipientName,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String counterpartyAccountCode,
        String fromAccountCode,
        String transactionCode,
        LocalDateTime occurredAt,
        String bankName
) {}
