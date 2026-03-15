package com.banco.co.transaction.repository;

import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Repository
public interface ITransactionRepository extends JpaRepository<Transaction, UUID> {

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA
    // ══════════════════════════════════════════════════════════

    @Query("SELECT t FROM Transaction t WHERE " +
            "(t.fromAccount.accountCode = :accountCode OR t.toAccount.accountCode = :accountCode) " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findAllByAccountCode(@Param("accountCode") String accountCode);

    @Query("SELECT t FROM Transaction t WHERE " +
            "t.fromAccount.accountCode = :accountCode " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findAllByFromAccount_AccountCode(@Param("accountCode") String accountCode);

    @Query("SELECT t FROM Transaction t WHERE " +
            "t.toAccount.accountCode = :accountCode " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findAllByToAccount_AccountCode(@Param("accountCode") String accountCode);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR USUARIO
    // ══════════════════════════════════════════════════════════

    @Query("SELECT t FROM Transaction t WHERE " +
            "(t.fromAccount.user.id = :userId OR t.toAccount.user.id = :userId) " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
            "ORDER BY t.createdAt DESC")
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

    List<Transaction> findByCategory(TransactionCategory category);

    @Query("SELECT t FROM Transaction t WHERE " +
            "t.category = :category " +
            "AND (t.fromAccount.user.id = :userId OR t.toAccount.user.id = :userId) " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findByCategoryAndUser(
            @Param("category") TransactionCategory category,
            @Param("userId") UUID userId
    );

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    @Query("SELECT t FROM Transaction t WHERE " +
            "(:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:category IS NULL OR t.category = :category) " +
            "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
            "AND (:accountCode IS NULL OR t.fromAccount.accountCode = :accountCode OR t.toAccount.accountCode = :accountCode) " +
            "ORDER BY t.createdAt DESC")
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

    List<Transaction> findByFlaggedForFraudTrue();

    @Query("SELECT t FROM Transaction t WHERE " +
            "t.flaggedForFraud = true " +
            "AND t.status = 'PENDING_REVIEW' " +
            "ORDER BY t.fraudScore DESC")
    List<Transaction> findSuspiciousTransactions();

    // ══════════════════════════════════════════════════════════
    //  TRANSACCIONES PROGRAMADAS
    // ══════════════════════════════════════════════════════════

    @Query("SELECT t FROM Transaction t WHERE " +
            "t.scheduledFor <= :now " +
            "AND t.status = 'SCHEDULED' " +
            "ORDER BY t.scheduledFor ASC")
    List<Transaction> findPendingScheduledTransactions(@Param("now") LocalDateTime now);

    // ══════════════════════════════════════════════════════════
    //  IDEMPOTENCIA
    // ══════════════════════════════════════════════════════════

    boolean existsByIdempotencyKey(String idempotencyKey);
}
