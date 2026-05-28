package com.banco.co.transaction.domain.port.out;

import com.banco.co.transaction.domain.model.Transaction;
import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — persistence contract for the transaction domain.
 * NO JPA imports, NO Spring Data JPA annotations (@Query, @Param, @EntityGraph).
 * NO JpaRepository extension.
 * Page/Pageable from spring-data-commons are allowed (pagination abstraction, not JPA).
 * Implementations live in adapter/out/jpa/TransactionJpaAdapter.
 */
public interface ITransactionRepository {

    // ══════════════════════════════════════════════════════════
    //  PERSISTENCIA
    // ══════════════════════════════════════════════════════════

    Transaction save(Transaction transaction);

    List<Transaction> saveAll(List<Transaction> transactions);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR ID
    // ══════════════════════════════════════════════════════════

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdWithAccounts(UUID id);

    Optional<Transaction> findByIdWithFromAccount(UUID id);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA
    // ══════════════════════════════════════════════════════════

    List<Transaction> findAllByAccountCode(String accountCode);

    List<Transaction> findAllByFromAccountCode(String accountCode);

    List<Transaction> findAllByToAccountCode(String accountCode);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR USUARIO
    // ══════════════════════════════════════════════════════════

    Page<Transaction> findUserTransactions(
            UUID userId,
            TransactionType type,
            TransactionStatus status,
            TransactionCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CATEGORÍA
    // ══════════════════════════════════════════════════════════

    List<Transaction> findByCategory(TransactionCategory category);

    List<Transaction> findByCategoryAndUser(TransactionCategory category, UUID userId);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    Page<Transaction> findAllWithFilters(
            TransactionType type,
            TransactionStatus status,
            TransactionCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String accountCode,
            Pageable pageable
    );

    // ══════════════════════════════════════════════════════════
    //  FRAUDE
    // ══════════════════════════════════════════════════════════

    List<Transaction> findByFlaggedForFraudTrue();

    List<Transaction> findSuspiciousTransactions();

    // ══════════════════════════════════════════════════════════
    //  TRANSACCIONES PROGRAMADAS
    // ══════════════════════════════════════════════════════════

    List<Transaction> findPendingScheduledTransactions(LocalDateTime now);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA + USUARIO (con filtros)
    // ══════════════════════════════════════════════════════════

    Page<Transaction> findAccountTransactionsByUser(
            String accountCode,
            UUID userId,
            TransactionType type,
            TransactionStatus status,
            TransactionCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    // ══════════════════════════════════════════════════════════
    //  IDEMPOTENCIA
    // ══════════════════════════════════════════════════════════

    boolean existsByIdempotencyKey(String idempotencyKey);
}
