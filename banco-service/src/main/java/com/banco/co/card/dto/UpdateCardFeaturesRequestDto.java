package com.banco.co.card.dto;

public record UpdateCardFeaturesRequestDto(
        Boolean contactlessEnabled,
        Boolean onlinePaymentsEnabled,
        Boolean internationalEnabled
) {}
