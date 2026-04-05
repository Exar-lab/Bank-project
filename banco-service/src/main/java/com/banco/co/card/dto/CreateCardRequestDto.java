package com.banco.co.card.dto;

import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCardRequestDto(
        @NotNull(message = "Card type is required") CardType cardType,
        @NotNull(message = "Card brand is required") CardBrand brand,
        @NotNull(message = "Card tier is required") CardTier tier,
        @NotBlank(message = "Account code is required") String accountCode
) {}
