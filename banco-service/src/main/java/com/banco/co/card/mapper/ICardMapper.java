package com.banco.co.card.mapper;

import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.dto.CardSummaryDto;
import com.banco.co.card.model.Card;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ICardMapper {

    @Mapping(target = "maskedCardNumber", expression = "java(maskCardNumber(card))")
    @Mapping(source = "account.accountCode", target = "accountCode")
    CardResponseDto toDto(Card card);

    @Mapping(target = "maskedCardNumber", expression = "java(maskCardNumber(card))")
    @Mapping(source = "account.accountCode", target = "accountCode")
    CardSummaryDto toSummaryDto(Card card);

    default String maskCardNumber(Card card) {
        String number = card.getCardNumber();
        if (number == null || number.length() < 4) return "****-****-****-????";
        return "****-****-****-" + number.substring(number.length() - 4);
    }
}
