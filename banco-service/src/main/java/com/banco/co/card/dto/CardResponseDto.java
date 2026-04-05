package com.banco.co.card.dto;

import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardResponseDto(
        String cardCode,
        String maskedCardNumber,
        CardType cardType,
        CardBrand brand,
        CardTier tier,
        CardStatus status,
        String blockedReason,
        String accountCode,
        LocalDateTime createdAt,
        LocalDateTime expirationDate,
        LocalDateTime activatedAt,
        LocalDateTime lastUsedAt,
        BigDecimal dailyLimit,
        BigDecimal monthlyLimit,
        BigDecimal dailySpent,
        BigDecimal monthlySpent,
        boolean contactlessEnabled,
        boolean onlinePaymentsEnabled,
        boolean internationalEnabled,
        Long points
) {}
