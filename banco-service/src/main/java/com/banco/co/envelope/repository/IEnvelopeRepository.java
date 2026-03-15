package com.banco.co.envelope.repository;

import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.model.Envelope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface IEnvelopeRepository extends JpaRepository<Envelope, UUID> {
    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS BÁSICAS
    // ══════════════════════════════════════════════════════════


    @Query("SELECT e FROM Envelope e WHERE e.envelopeCode = :envelopeCode AND e.status = 'ACTIVE'")
    Optional<Envelope> findActiveByEnvelopeCode(@Param("envelopeCode") String envelopeCode);

    @Query("SELECT e FROM Envelope e WHERE e.account.accountCode = :accountCode AND e.status = 'ACTIVE'")
    List<Envelope> findActiveByAccountCode(@Param("accountCode") String accountCode);

    @Query("SELECT e FROM Envelope e WHERE e.account.accountCode = :accountCode AND e.status = :status")
    List<Envelope> findByAccount_AccountCodeAndStatus(
            @Param("accountCode") String accountCode,
            @Param("status") EnvelopeStatus status
    );

    @Query("SELECT e FROM Envelope e WHERE e.account.accountCode = :accountCode AND e.status = 'ACTIVE' ORDER BY e.createdAt DESC")
    List<Envelope> findAllByAccountCodeOrderByCreatedAtDesc(@Param("accountCode") String accountCode);

    // Contar envelopes por cuenta y status
    long countByAccount_IdAndStatus(UUID accountId, EnvelopeStatus status);

    @Query("SELECT e FROM Envelope e WHERE e.account.accountCode = :accountCode AND e.createdAt > :date AND e.status = 'ACTIVE'")
    List<Envelope> findActiveCreatedAfter(
            @Param("date") LocalDateTime date,
            @Param("accountCode") String accountCode
    );

    // Por usuario
    @Query("SELECT e FROM Envelope e WHERE e.account.user.id = :userId AND e.status = :status")
    List<Envelope> findByAccount_User_IdAndStatus(
            @Param("userId") UUID userId,
            @Param("status") EnvelopeStatus status
    );

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS CON JOINS (Optimizadas)
    // ══════════════════════════════════════════════════════════

    @Query("SELECT e FROM Envelope e " +
            "LEFT JOIN FETCH e.account a " +
            "WHERE a.accountCode = :accountCode AND e.status = 'ACTIVE'")
    List<Envelope> findAllActiveByAccountCodeWithAccount(@Param("accountCode") String accountCode);

    @Query("SELECT e FROM Envelope e " +
            "LEFT JOIN FETCH e.account a " +
            "WHERE e.envelopeCode = :envelopeCode AND e.status = 'ACTIVE'")
    Optional<Envelope> findActiveByAccountCodeWithAccount(@Param("envelopeCode") String envelopeCode);

    @Query("SELECT e FROM Envelope e " +
            "LEFT JOIN FETCH e.account a " +
            "LEFT JOIN FETCH a.user u " +
            "WHERE e.envelopeCode = :envelopeCode AND e.status = 'ACTIVE'")
    Optional<Envelope> findActiveByEnvelopeCodeWithAccountAndUser(@Param("envelopeCode") String envelopeCode);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR STATUS Y TIPO
    // ══════════════════════════════════════════════════════════

    List<Envelope> findAllByStatus(EnvelopeStatus status);

    @Query("SELECT e FROM Envelope e WHERE e.type = :type AND e.status = 'ACTIVE'")
    List<Envelope> findActiveByType(@Param("type") EnvelopeType type);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS PARA SCHEDULED TASKS
    // ══════════════════════════════════════════════════════════

    @Query("SELECT e FROM Envelope e WHERE " +
            "e.autoContribute = true AND " +
            "e.nextContributionDate <= :now AND " +
            "e.status = 'ACTIVE'")
    List<Envelope> findPendingAutoContributions(@Param("now") LocalDate now);








}
