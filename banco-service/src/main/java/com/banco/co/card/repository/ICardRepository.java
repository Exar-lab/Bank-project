package com.banco.co.card.repository;

import com.banco.co.card.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICardRepository extends JpaRepository<Card, UUID> {

    @Transactional(readOnly = true)
    @Query("SELECT c FROM Card c JOIN FETCH c.account a JOIN FETCH a.user WHERE c.cardCode = :cardCode")
    Optional<Card> findByCardCodeWithAccount(@Param("cardCode") String cardCode);
}
