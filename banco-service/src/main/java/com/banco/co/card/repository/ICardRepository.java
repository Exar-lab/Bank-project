package com.banco.co.card.repository;

import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.model.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICardRepository extends JpaRepository<Card, UUID> {

    @Transactional(readOnly = true)
    @Query("SELECT c FROM Card c JOIN FETCH c.account a JOIN FETCH a.user WHERE c.cardCode = :cardCode")
    Optional<Card> findByCardCodeWithAccount(@Param("cardCode") String cardCode);

    @Transactional(readOnly = true)
    @Query("SELECT c FROM Card c JOIN FETCH c.account a JOIN FETCH a.user u WHERE u.email = :email")
    List<Card> findAllByAccountUserEmail(@Param("email") String email);

    @Transactional(readOnly = true)
    @Query("SELECT c FROM Card c JOIN FETCH c.account a WHERE a.accountCode = :accountCode")
    List<Card> findAllByAccountAccountCode(@Param("accountCode") String accountCode);

    @Transactional(readOnly = true)
    Optional<Card> findByCardCode(String cardCode);

    @Query(
        value = "SELECT c FROM Card c JOIN FETCH c.account a WHERE c.status = :status",
        countQuery = "SELECT COUNT(c) FROM Card c WHERE c.status = :status"
    )
    @Transactional(readOnly = true)
    Page<Card> findAllByStatus(@Param("status") CardStatus status, Pageable pageable);

    boolean existsByCardCode(String cardCode);

    @Transactional(readOnly = true)
    @Query("SELECT c FROM Card c JOIN FETCH c.account a JOIN FETCH a.user u WHERE u.email = :email AND c.status = :status")
    List<Card> findAllByAccountUserEmailAndStatus(@Param("email") String email, @Param("status") CardStatus status);
}
