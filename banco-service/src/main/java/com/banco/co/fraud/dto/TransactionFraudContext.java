package com.banco.co.fraud.dto;

import java.math.BigDecimal;

public record TransactionFraudContext(
    // --- Existing fields (kept in original order) ---
    String transactionId,       // UUID from "transactionId" payload field, used as correlation ID
    String fromAccount,
    String toAccount,           // nullable — null for withdrawals/payments
    BigDecimal amount,
    String currency,
    // --- New fields ---
    String transactionCode,     // human-readable code (TXN-BCR-...)
    String transactionType,     // TransactionType.name() — String avoids cross-module enum coupling
    String channel,             // TransactionChannel.name() or null — String avoids cross-module enum coupling
    String merchantName,        // nullable
    String merchantMccCode,     // nullable
    String ipAddress,           // nullable
    String deviceId,            // nullable
    String locationCountry      // nullable
) {}
