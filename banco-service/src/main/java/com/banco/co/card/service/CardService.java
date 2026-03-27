package com.banco.co.card.service;

import com.banco.co.card.exception.card.CardNotFoundException;
import com.banco.co.card.model.Card;
import com.banco.co.card.repository.ICardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class CardService implements ICardService {

    private final ICardRepository cardRepository;

    public CardService(ICardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Card findCardWithAccountByCardCode(String cardCode) {
        return cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));
    }
}
