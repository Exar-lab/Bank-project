package com.banco.co.transaction.dto;

import com.banco.co.transaction.enums.TransactionCategory;

import java.math.BigDecimal;
import java.util.Map;

// CategorySummaryDto.java
public record CategorySummaryDto(
        Map<TransactionCategory, BigDecimal> totalByCategory,
        BigDecimal totalSpent,
        TransactionCategory topCategory,
        int transactionCount
) {}
