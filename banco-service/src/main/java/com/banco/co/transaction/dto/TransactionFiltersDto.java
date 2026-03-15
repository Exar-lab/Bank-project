package com.banco.co.transaction.dto;

import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;

import java.time.LocalDateTime;

// TransactionFiltersDto.java
public record TransactionFiltersDto(
        TransactionType type,
        TransactionStatus status,
        TransactionCategory category,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String accountCode
) {}
