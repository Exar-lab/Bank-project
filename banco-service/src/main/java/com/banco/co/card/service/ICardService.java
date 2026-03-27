package com.banco.co.card.service;

import com.banco.co.card.model.Card;

public interface ICardService {

    /**
     * Find a card with its associated account and user eagerly loaded.
     * USO INTERNO: For TransactionService payment processing.
     */
    Card findCardWithAccountByCardCode(String cardCode);
}
