package com.banco.co.transaction.adapter.out.jpa;

import com.banco.co.account.adapter.out.jpa.AccountEntity;
import com.banco.co.account.adapter.out.jpa.IAccountJpaRepository;
import com.banco.co.card.model.Card;
import com.banco.co.card.repository.ICardRepository;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.envelope.repository.IEnvelopeRepository;
import com.banco.co.transaction.domain.model.Transaction;
import com.banco.co.transaction.domain.port.out.ITransactionRepository;
import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter that implements the output port ITransactionRepository.
 * Delegates to ITransactionJpaRepository (Spring Data) and converts via TransactionEntityMapper.
 *
 * FK resolution rules for save():
 *   - fromAccount, toAccount: REQUIRED — orElseThrow if UUID is non-null but entity not found.
 *   - card, envelope, originalTransaction: NULLABLE — only load if UUID is non-null.
 *
 * @Transactional belongs on the service (application) layer, NOT here.
 */
@Component
public class TransactionJpaAdapter implements ITransactionRepository {

    private final ITransactionJpaRepository jpaRepo;
    private final TransactionEntityMapper mapper;
    private final IAccountJpaRepository accountJpaRepo;
    private final ICardRepository cardRepo;
    private final IEnvelopeRepository envelopeRepo;

    public TransactionJpaAdapter(
            ITransactionJpaRepository jpaRepo,
            TransactionEntityMapper mapper,
            IAccountJpaRepository accountJpaRepo,
            ICardRepository cardRepo,
            IEnvelopeRepository envelopeRepo) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
        this.accountJpaRepo = accountJpaRepo;
        this.cardRepo = cardRepo;
        this.envelopeRepo = envelopeRepo;
    }

    // ══════════════════════════════════════════════════════════
    //  PERSISTENCIA
    // ══════════════════════════════════════════════════════════

    @Override
    public Transaction save(Transaction domain) {
        domain.initializeTransactionData();
        TransactionEntity entity = mapper.toEntity(domain);
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        resolveAndSetForeignKeys(domain, entity);
        return mapper.toDomain(jpaRepo.save(entity));
    }

    @Override
    public List<Transaction> saveAll(List<Transaction> transactions) {
        List<TransactionEntity> entities = transactions.stream()
                .map(domain -> {
                    domain.initializeTransactionData();
                    TransactionEntity entity = mapper.toEntity(domain);
                    if (domain.getId() != null) {
                        entity.setId(domain.getId());
                    }
                    resolveAndSetForeignKeys(domain, entity);
                    return entity;
                })
                .toList();
        return jpaRepo.saveAll(entities).stream()
                .map(mapper::toDomain)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR ID
    // ══════════════════════════════════════════════════════════

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpaRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdWithAccounts(UUID id) {
        return jpaRepo.findByIdWithAccounts(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdWithFromAccount(UUID id) {
        return jpaRepo.findByIdWithFromAccount(id).map(mapper::toDomain);
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    public List<Transaction> findAllByAccountCode(String accountCode) {
        return jpaRepo.findAllByAccountCode(accountCode).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findAllByFromAccountCode(String accountCode) {
        return jpaRepo.findAllByFromAccountCode(accountCode).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findAllByToAccountCode(String accountCode) {
        return jpaRepo.findAllByToAccountCode(accountCode).stream()
                .map(mapper::toDomain)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR USUARIO
    // ══════════════════════════════════════════════════════════

    @Override
    public Page<Transaction> findUserTransactions(
            UUID userId,
            TransactionType type,
            TransactionStatus status,
            TransactionCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return jpaRepo.findUserTransactions(userId, type, status, category, startDate, endDate, pageable)
                .map(mapper::toDomain);
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CATEGORÍA
    // ══════════════════════════════════════════════════════════

    @Override
    public List<Transaction> findByCategory(TransactionCategory category) {
        return jpaRepo.findByCategory(category).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findByCategoryAndUser(TransactionCategory category, UUID userId) {
        return jpaRepo.findByCategoryAndUser(category, userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    @Override
    public Page<Transaction> findAllWithFilters(
            TransactionType type,
            TransactionStatus status,
            TransactionCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String accountCode,
            Pageable pageable) {
        return jpaRepo.findAllWithFilters(type, status, category, startDate, endDate, accountCode, pageable)
                .map(mapper::toDomain);
    }

    // ══════════════════════════════════════════════════════════
    //  FRAUDE
    // ══════════════════════════════════════════════════════════

    @Override
    public List<Transaction> findByFlaggedForFraudTrue() {
        return jpaRepo.findByFlaggedForFraudTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findSuspiciousTransactions() {
        return jpaRepo.findSuspiciousTransactions().stream()
                .map(mapper::toDomain)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  TRANSACCIONES PROGRAMADAS
    // ══════════════════════════════════════════════════════════

    @Override
    public List<Transaction> findPendingScheduledTransactions(LocalDateTime now) {
        return jpaRepo.findPendingScheduledTransactions(now).stream()
                .map(mapper::toDomain)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR CUENTA + USUARIO (con filtros)
    // ══════════════════════════════════════════════════════════

    @Override
    public Page<Transaction> findAccountTransactionsByUser(
            String accountCode,
            UUID userId,
            TransactionType type,
            TransactionStatus status,
            TransactionCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return jpaRepo.findAccountTransactionsByUser(
                accountCode, userId, type, status, category, startDate, endDate, pageable)
                .map(mapper::toDomain);
    }

    // ══════════════════════════════════════════════════════════
    //  IDEMPOTENCIA
    // ══════════════════════════════════════════════════════════

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return jpaRepo.existsByIdempotencyKey(idempotencyKey);
    }

    // ══════════════════════════════════════════════════════════
    //  FK RESOLUTION — private helper
    // ══════════════════════════════════════════════════════════

    /**
     * Resolves cross-feature JPA entity references from domain UUID fields.
     * - fromAccount / toAccount: REQUIRED when UUID is non-null (orElseThrow).
     * - card / envelope / originalTransaction: NULLABLE (only loaded when UUID is non-null).
     */
    private void resolveAndSetForeignKeys(Transaction domain, TransactionEntity entity) {
        // fromAccount — REQUIRED if UUID is set
        if (domain.getFromAccountId() != null) {
            AccountEntity fromAccount = accountJpaRepo.findById(domain.getFromAccountId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot save transaction: fromAccount not found with id=" + domain.getFromAccountId()));
            entity.setFromAccount(fromAccount);
        }

        // toAccount — REQUIRED if UUID is set
        if (domain.getToAccountId() != null) {
            AccountEntity toAccount = accountJpaRepo.findById(domain.getToAccountId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot save transaction: toAccount not found with id=" + domain.getToAccountId()));
            entity.setToAccount(toAccount);
        }

        // card — NULLABLE
        if (domain.getCardId() != null) {
            Card card = cardRepo.findById(domain.getCardId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot save transaction: card not found with id=" + domain.getCardId()));
            entity.setCard(card);
        }

        // envelope — NULLABLE
        if (domain.getEnvelopeId() != null) {
            Envelope envelope = envelopeRepo.findById(domain.getEnvelopeId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot save transaction: envelope not found with id=" + domain.getEnvelopeId()));
            entity.setEnvelope(envelope);
        }

        // originalTransaction — NULLABLE (self-reference for reversals)
        if (domain.getOriginalTransactionId() != null) {
            TransactionEntity originalTransaction = jpaRepo.findById(domain.getOriginalTransactionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot save transaction: originalTransaction not found with id=" + domain.getOriginalTransactionId()));
            entity.setOriginalTransaction(originalTransaction);
        }
    }
}
