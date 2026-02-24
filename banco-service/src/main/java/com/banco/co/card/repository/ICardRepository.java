package com.banco.co.card.repository;

import com.banco.co.card.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface ICardRepository extends JpaRepository<Card, UUID> {
}
