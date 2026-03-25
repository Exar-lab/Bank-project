package com.banco.co.envelope.repository;

import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.model.Envelope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a LEFT JOIN FETCH a.user u WHERE e.envelopeCode = :envelopeCode AND e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Envelope> findActiveByEnvelopeCode(@Param("envelopeCode") String envelopeCode);

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a WHERE a.accountCode = :accountCode AND e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<Envelope> findActiveByAccountCode(@Param("accountCode") String accountCode);

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a WHERE a.accountCode = :accountCode AND e.status = :status")
    @Transactional(readOnly = true)
    List<Envelope> findByAccount_AccountCodeAndStatus(
            @Param("accountCode") String accountCode,
            @Param("status") EnvelopeStatus status
    );

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a WHERE a.accountCode = :accountCode AND e.status = 'ACTIVE' ORDER BY e.createdAt DESC")
    @Transactional(readOnly = true)
    List<Envelope> findAllByAccountCodeOrderByCreatedAtDesc(@Param("accountCode") String accountCode);

    // Contar envelopes por cuenta y status
    @Transactional(readOnly = true)
    long countByAccount_IdAndStatus(UUID accountId, EnvelopeStatus status);

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a WHERE a.accountCode = :accountCode AND e.createdAt > :date AND e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<Envelope> findActiveCreatedAfter(
            @Param("date") LocalDateTime date,
            @Param("accountCode") String accountCode
    );

    // Por usuario
    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a LEFT JOIN FETCH a.user u WHERE u.id = :userId AND e.status = :status")
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    List<Envelope> findAllActiveByAccountCodeWithAccount(@Param("accountCode") String accountCode);

    @Query("SELECT e FROM Envelope e " +
            "LEFT JOIN FETCH e.account a " +
            "WHERE e.envelopeCode = :envelopeCode AND e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Envelope> findActiveByAccountCodeWithAccount(@Param("envelopeCode") String envelopeCode);

    @Query("SELECT e FROM Envelope e " +
            "LEFT JOIN FETCH e.account a " +
            "LEFT JOIN FETCH a.user u " +
            "WHERE e.envelopeCode = :envelopeCode AND e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Envelope> findActiveByEnvelopeCodeWithAccountAndUser(@Param("envelopeCode") String envelopeCode);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR STATUS Y TIPO
    // ══════════════════════════════════════════════════════════

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a LEFT JOIN FETCH a.user u WHERE e.status = :status")
    @Transactional(readOnly = true)
    List<Envelope> findAllByStatus(@Param("status") EnvelopeStatus status);

    @Query("SELECT e FROM Envelope e JOIN FETCH e.account a JOIN FETCH a.user u WHERE e.status = :status AND u.id = :userId")
    @Transactional(readOnly = true)
    List<Envelope> findByStatusAndUserId(@Param("status") EnvelopeStatus status, @Param("userId") UUID userId);

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a WHERE e.type = :type AND e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<Envelope> findActiveByType(@Param("type") EnvelopeType type);

    @Query("SELECT e FROM Envelope e JOIN FETCH e.account a JOIN FETCH a.user u WHERE e.type = :type AND e.status = 'ACTIVE' AND u.id = :userId")
    @Transactional(readOnly = true)
    List<Envelope> findActiveByTypeAndUserId(@Param("type") EnvelopeType type, @Param("userId") UUID userId);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS PARA SCHEDULED TASKS
    // ══════════════════════════════════════════════════════════

    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.account a WHERE " +
            "e.autoContribute = true AND " +
            "e.nextContributionDate <= :now AND " +
            "e.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<Envelope> findPendingAutoContributions(@Param("now") LocalDate now);

}
