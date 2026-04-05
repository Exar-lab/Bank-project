package com.banco.co.card.dto;

import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import java.time.LocalDateTime;

public record CardSummaryDto(
        String cardCode,
        String maskedCardNumber,
        CardType cardType,
        CardBrand brand,
        CardTier tier,
        CardStatus status,
        String accountCode,
        LocalDateTime expirationDate
) {}
