package com.banco.co.transaction.repository;

import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITransactionRepository extends JpaRepository<Transaction, UUID> {

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR ID (con asociaciones)
    // ══════════════════════════════════════════════════════════

    @Query("SELECT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE t.id = :id")
    @Transactional(readOnly = true)
    Optional<Transaction> findByIdWithAccounts(@Param("id") UUID id);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA
    // ══════════════════════════════════════════════════════════

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE (fa.accountCode = :accountCode OR ta.accountCode = :accountCode) " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    List<Transaction> findAllByAccountCode(@Param("accountCode") String accountCode);

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "WHERE fa.accountCode = :accountCode " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    List<Transaction> findAllByFromAccount_AccountCode(@Param("accountCode") String accountCode);

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE ta.accountCode = :accountCode " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    List<Transaction> findAllByToAccount_AccountCode(@Param("accountCode") String accountCode);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR USUARIO
    // ══════════════════════════════════════════════════════════

    @EntityGraph(attributePaths = {
            "fromAccount", "fromAccount.user",
            "toAccount", "toAccount.user"
    })
    @Query("SELECT t FROM Transaction t WHERE " +
            "(t.fromAccount.user.id = :userId OR t.toAccount.user.id = :userId) " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    Page<Transaction> findUserTransactions(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("category") TransactionCategory category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CATEGORÍA
    // ══════════════════════════════════════════════════════════

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE t.category = :category ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    List<Transaction> findByCategory(@Param("category") TransactionCategory category);

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE t.category = :category " +
            "AND (fa.user.id = :userId OR ta.user.id = :userId) " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    List<Transaction> findByCategoryAndUser(
            @Param("category") TransactionCategory category,
            @Param("userId") UUID userId
    );

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    @EntityGraph(attributePaths = {
            "fromAccount", "fromAccount.user",
            "toAccount", "toAccount.user"
    })
    @Query("SELECT t FROM Transaction t WHERE " +
            "(:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
            "AND (:accountCode IS NULL OR t.fromAccount.accountCode = :accountCode OR t.toAccount.accountCode = :accountCode) " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    Page<Transaction> findAllWithFilters(
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("category") TransactionCategory category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("accountCode") String accountCode,
            Pageable pageable
    );

    // ══════════════════════════════════════════════════════════
    //  FRAUDE
    // ══════════════════════════════════════════════════════════

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE t.flaggedForFraud = true ORDER BY t.fraudScore DESC")
    @Transactional(readOnly = true)
    List<Transaction> findByFlaggedForFraudTrue();

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE t.flaggedForFraud = true AND t.status = 'PENDING_REVIEW' " +
            "ORDER BY t.fraudScore DESC")
    @Transactional(readOnly = true)
    List<Transaction> findSuspiciousTransactions();

    // ══════════════════════════════════════════════════════════
    //  TRANSACCIONES PROGRAMADAS
    // ══════════════════════════════════════════════════════════

    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user " +
            "LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user " +
            "WHERE t.scheduledFor <= :now AND t.status = 'SCHEDULED' " +
            "ORDER BY t.scheduledFor ASC")
    @Transactional(readOnly = true)
    List<Transaction> findPendingScheduledTransactions(@Param("now") LocalDateTime now);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA + USUARIO (con filtros)
    // ══════════════════════════════════════════════════════════

    @EntityGraph(attributePaths = {
            "fromAccount", "fromAccount.user",
            "toAccount", "toAccount.user"
    })
    @Query("SELECT t FROM Transaction t WHERE " +
            "(t.fromAccount.accountCode = :accountCode OR t.toAccount.accountCode = :accountCode) " +
            "AND (t.fromAccount.user.id = :userId OR t.toAccount.user.id = :userId) " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
            "ORDER BY t.createdAt DESC")
    @Transactional(readOnly = true)
    Page<Transaction> findAccountTransactionsByUser(
            @Param("accountCode") String accountCode,
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("category") TransactionCategory category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // ══════════════════════════════════════════════════════════
    //  IDEMPOTENCIA
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    boolean existsByIdempotencyKey(String idempotencyKey);
}
