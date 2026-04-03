package com.banco.co.fraud.riskprofile.dto;

import java.math.BigDecimal;

public record TransactionCompletedRiskEvent(
        String eventId,
        String transactionId,
        String accountCode,
        BigDecimal amount,
        String status
) {
}
