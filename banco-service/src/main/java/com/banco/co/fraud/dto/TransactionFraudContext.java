package com.banco.co.fraud.dto;

import java.math.BigDecimal;

public record TransactionFraudContext(
    String transactionId,
    String fromAccount,
    String toAccount,
    BigDecimal amount,
    String currency
) {}
