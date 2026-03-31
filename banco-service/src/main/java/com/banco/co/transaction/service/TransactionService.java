package com.banco.co.transaction.service;

import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.card.model.Card;
import com.banco.co.card.service.ICardService;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.exception.fraud.FraudBlockedException;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.service.IFraudDetectionService;
import com.banco.co.transaction.dto.CategorySummaryDto;
import com.banco.co.transaction.dto.ScheduledTransferRequestDto;
import com.banco.co.transaction.dto.TransactionFiltersDto;
import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.CashDepositRequestDto;
import com.banco.co.transaction.dto.movement.CashWithdrawalRequestDto;
import com.banco.co.transaction.dto.movement.CheckDepositRequestDto;
import com.banco.co.transaction.dto.movement.TransferRequestDto;
import com.banco.co.transaction.dto.payment.PaymentRequestDto;
import com.banco.co.transaction.dto.payment.ServicePaymentRequestDto;
import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.transaction.exception.transaction.TransactionDeclinedException;
import com.banco.co.transaction.exception.transaction.TransactionInvalidException;
import com.banco.co.transaction.exception.transaction.TransactionNotFoundException;
import com.banco.co.transaction.exception.transaction.TransactionStatusException;
import com.banco.co.transaction.mapper.ITransactionMapper;
import com.banco.co.transaction.model.Transaction;
import com.banco.co.transaction.utils.metadata.ITransactionMetadataEnricher;
import com.banco.co.outbox.enums.KafkaTopic;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.transaction.repository.ITransactionRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService implements ITransactionService {

    private final IAccountService accountService;
    private final ITransactionRepository transactionRepository;
    private final IUserService userService;
    private final IAuditLogService auditLogService;
    private final ITransactionMapper transactionMapper;
    private final ITransactionMetadataEnricher transactionMetadataEnricher;
    private final IOutboxEventPort outboxEventPort;
    private final ICardService cardService;
    private final ObjectMapper objectMapper;
    private final IFraudDetectionService fraudDetectionService;

    private static final BigDecimal SUSPICIOUS_FRAUD_SCORE = BigDecimal.valueOf(75);

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES DIGITALES
    // ══════════════════════════════════════════════════════════

    @Transactional(noRollbackFor = FraudBlockedException.class)
    @Override
    public TransactionResponseDto transfer(TransferRequestDto dto, String userEmail, TransactionRequestMetadataDto metadata) {
        User user = userService.getEntityUserByEmail(userEmail);

        Account fromAccount = accountService.findAccountWithUserByAccountCode(dto.fromAccountCode());
        Account toAccount = accountService.findAccountWithUserByAccountCode(dto.toAccountCode());

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            auditLogService.logFailure(
                    user,
                    AuditAction.TRANSACTION_FAILED,
                    AuditEntityType.TRANSACTION,
                    List.of(new AuditLogDetail("message", "Attempted transfer from account not owned"))
            );
            throw new UnauthorizedException("You don't own the source account");
        }

        if (dto.fromAccountCode().equals(dto.toAccountCode())) {
            throw new TransactionInvalidException(dto.fromAccountCode(), "Cannot transfer to the same account");
        }

        accountService.validateCanWithdraw(fromAccount, dto.amount());
        accountService.validateCanReceiveDeposit(toAccount);

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.TRANSFER);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : "Transferencia");
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());
        transaction.setToAccountBalanceBefore(toAccount.getBalance());

        // PROCESSING phase - reservar fondos en origen
        transaction.process();

        fromAccount.blockFunds(dto.amount());

        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());

        // Save BEFORE fraud gate so @PrePersist fires (generates transactionCode + UUID)
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Fraud gate — SUSPICIOUS returns false, BLOCKED throws, CLEAR returns true
        if (!executeFraudGate(savedTransaction, fromAccount, dto.amount())) {
            accountService.updateBalance(fromAccount);
            return transactionMapper.toDto(savedTransaction);
        }

        // CLEAR path — COMPLETING phase: confirmar salida definitiva y acreditar destino
        savedTransaction.complete();

        fromAccount.confirmBlockedFunds(dto.amount());
        toAccount.deposit(dto.amount());

        savedTransaction.setToAccountBalanceAfter(toAccount.getBalance());

        transactionRepository.save(savedTransaction);
        accountService.updateBalance(fromAccount);
        accountService.updateBalance(toAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Transfer created"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("fromAccount", fromAccount.getAccountCode()),
                        new AuditLogDetail("toAccount", toAccount.getAccountCode())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, "TransactionCompleted"),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Transfer completed: {} from {} to {}",
                dto.amount(), fromAccount.getAccountCode(), toAccount.getAccountCode());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional(noRollbackFor = FraudBlockedException.class)
    @Override
    public TransactionResponseDto payment(PaymentRequestDto dto, String userEmail, TransactionRequestMetadataDto metadata) {
        User user = userService.getEntityUserByEmail(userEmail);

        Card card = cardService.findCardWithAccountByCardCode(dto.cardCode());

        Account fromAccount = card.getAccount();

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            auditLogService.logFailure(
                    user,
                    AuditAction.TRANSACTION_FAILED,
                    AuditEntityType.TRANSACTION,
                    List.of(new AuditLogDetail("message", "Attempted payment with card not owned"))
            );
            throw new UnauthorizedException("You don't own this card");
        }

        if (!card.canTransact(dto.amount())) {
            throw new TransactionDeclinedException(dto.cardCode(), "Card cannot be used for this payment");
        }

        accountService.validateCanWithdraw(fromAccount, dto.amount());

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.PAYMENT);
        transaction.setFromAccount(fromAccount);
        transaction.setCard(card);
        transaction.setCardLastFourDigits(card.getCardNumber() != null
                ? card.getCardNumber().substring(Math.max(0, card.getCardNumber().length() - 4))
                : null);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : "Pago con tarjeta");
        transaction.setMerchantName(dto.merchantName());
        transaction.setMerchantMccCode(dto.merchantMccCode());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());

        transaction.process();

        fromAccount.blockFunds(dto.amount());
        card.recordTransaction(dto.amount(), TransactionType.PAYMENT);

        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());

        // Save BEFORE fraud gate so @PrePersist fires (generates transactionCode + UUID)
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Fraud gate — SUSPICIOUS returns false, BLOCKED throws, CLEAR returns true
        if (!executeFraudGate(savedTransaction, fromAccount, dto.amount())) {
            accountService.updateBalance(fromAccount);
            return transactionMapper.toDto(savedTransaction);
        }

        // CLEAR path
        savedTransaction.complete();

        fromAccount.confirmBlockedFunds(dto.amount());

        transactionRepository.save(savedTransaction);
        accountService.updateBalance(fromAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card payment completed"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("merchant", dto.merchantName()),
                        new AuditLogDetail("cardCode", dto.cardCode())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, "TransactionCompleted"),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Payment completed: {} from card {} at merchant {}",
                dto.amount(), dto.cardCode(), dto.merchantName());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional(noRollbackFor = FraudBlockedException.class)
    @Override
    public TransactionResponseDto payService(ServicePaymentRequestDto dto, String userEmail, TransactionRequestMetadataDto metadata) {
        // TODO: Validar que el servicio sea legítimo.
        User user = userService.getEntityUserByEmail(userEmail);

        Account fromAccount = accountService.findAccountWithUserByAccountCode(dto.accountCode());

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            auditLogService.logFailure(
                    user,
                    AuditAction.TRANSACTION_FAILED,
                    AuditEntityType.TRANSACTION,
                    List.of(new AuditLogDetail("message", "Attempted service payment from account not owned"))
            );
            throw new UnauthorizedException("You don't own the source account");
        }

        accountService.validateCanWithdraw(fromAccount, dto.amount());

        String description = String.format("Pago de servicio: %s - Ref: %s",
                dto.serviceProvider(), dto.referenceNumber());

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.PAYMENT);
        transaction.setFromAccount(fromAccount);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : description);
        transaction.setMerchantName(dto.serviceProvider());
        transaction.setExternalReference(dto.referenceNumber());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());

        transaction.process();

        fromAccount.blockFunds(dto.amount());

        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());

        // Save BEFORE fraud gate so @PrePersist fires (generates transactionCode + UUID)
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Fraud gate — SUSPICIOUS returns false, BLOCKED throws, CLEAR returns true
        if (!executeFraudGate(savedTransaction, fromAccount, dto.amount())) {
            accountService.updateBalance(fromAccount);
            return transactionMapper.toDto(savedTransaction);
        }

        // CLEAR path
        savedTransaction.complete();

        fromAccount.confirmBlockedFunds(dto.amount());

        transactionRepository.save(savedTransaction);
        accountService.updateBalance(fromAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Service payment completed"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("serviceProvider", dto.serviceProvider()),
                        new AuditLogDetail("referenceNumber", dto.referenceNumber())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, "TransactionCompleted"),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Service payment completed: {} from {} to provider {}",
                dto.amount(), dto.accountCode(), dto.serviceProvider());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional
    @Override
    public TransactionResponseDto cashDeposit(CashDepositRequestDto dto, String employeeEmail, TransactionRequestMetadataDto metadata) {
        // 1. Validar empleado
        User employee = userService.getEntityUserByEmail(employeeEmail);

        // 2. Obtener cuenta destino
        Account toAccount = accountService.findAccountWithUserByAccountCode(dto.accountCode());

        // 3. Validar que la cuenta puede recibir depósitos
        accountService.validateCanReceiveDeposit(toAccount);

        // 4. Crear transacción
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setFromAccount(null);
        transaction.setToAccount(toAccount);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : "Cash deposit at branch");
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        // 5. Enriquecer metadata
        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.BRANCH);

        // 6. Guardar balance antes
        transaction.setToAccountBalanceBefore(toAccount.getBalance());

        // 7. Procesar
        transaction.process();

        // 8. Ejecutar depósito
        toAccount.deposit(dto.amount());

        // 9. Guardar balance después
        transaction.setToAccountBalanceAfter(toAccount.getBalance());

        // 10. Completar
        transaction.complete();

        // 11. Persistir
        Transaction savedTransaction = transactionRepository.save(transaction);
        accountService.updateBalance(toAccount);

        // 12. Auditar
        auditLogService.logSuccess(
                employee,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Cash deposit created"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("toAccount", toAccount.getAccountCode())
                )
        );

        // 13. Publicar evento al outbox
        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, null, toAccount, dto.amount()),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Cash deposit completed: {} to {}", dto.amount(), toAccount.getAccountCode());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional(noRollbackFor = FraudBlockedException.class)
    @Override
    public TransactionResponseDto cashWithdrawal(CashWithdrawalRequestDto dto, String employeeEmail, TransactionRequestMetadataDto metadata) {
        // 1. Validar empleado
        User employee = userService.getEntityUserByEmail(employeeEmail);

        // 2. Validar verificación de identidad del cliente
        if (!Boolean.TRUE.equals(dto.customerIdVerified())) {
            throw new TransactionInvalidException(dto.accountCode(), "Customer identity not verified");
        }

        // 3. Obtener cuenta origen
        Account fromAccount = accountService.findAccountWithUserByAccountCode(dto.accountCode());

        // 4. Validar que la cuenta puede hacer retiros
        accountService.validateCanWithdraw(fromAccount, dto.amount());

        // 5. Crear transacción
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.WITHDRAWAL);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(null);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : "Cash withdrawal at branch");
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        // 6. Enriquecer metadata
        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.BRANCH);

        // 7. Guardar balance antes
        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());

        // 8. Procesar
        transaction.process();

        // 9. PROCESSING phase - reservar fondos
        fromAccount.blockFunds(dto.amount());

        // 10. Guardar balance después del bloqueo
        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());

        // 11. Save BEFORE fraud gate so @PrePersist fires (generates transactionCode + UUID)
        Transaction savedTransaction = transactionRepository.save(transaction);

        // 12. Fraud gate — SUSPICIOUS returns false, BLOCKED throws, CLEAR returns true
        if (!executeFraudGate(savedTransaction, fromAccount, dto.amount())) {
            accountService.updateBalance(fromAccount);
            return transactionMapper.toDto(savedTransaction);
        }

        // 13. CLEAR path — completar y confirmar salida definitiva
        savedTransaction.complete();

        // COMPLETING phase - confirmar salida definitiva (efectivo entregado físicamente)
        fromAccount.confirmBlockedFunds(dto.amount());

        // 14. Persistir
        transactionRepository.save(savedTransaction);
        accountService.updateBalance(fromAccount);

        // 13. Auditar
        auditLogService.logSuccess(
                employee,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Cash withdrawal created"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("fromAccount", fromAccount.getAccountCode())
                )
        );

        // 14. Publicar evento al outbox
        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, fromAccount, null, dto.amount()),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Cash withdrawal completed: {} from {}", dto.amount(), fromAccount.getAccountCode());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional
    @Override
    public TransactionResponseDto checkDeposit(CheckDepositRequestDto dto, String employeeEmail, TransactionRequestMetadataDto metadata) {
        // 1. Validar empleado
        User employee = userService.getEntityUserByEmail(employeeEmail);

        // 2. Obtener cuenta destino
        Account toAccount = accountService.findAccountWithUserByAccountCode(dto.accountCode());

        // 3. Validar que la cuenta puede recibir depósitos
        accountService.validateCanReceiveDeposit(toAccount);

        // 4. Crear transacción
        String checkDescription = "Check #" + dto.checkNumber() + " from " + dto.bankName();
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setFromAccount(null);
        transaction.setToAccount(toAccount);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : checkDescription);
        transaction.setNotes(checkDescription);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        // 5. Enriquecer metadata
        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.BRANCH);

        // 6. Guardar balance antes
        transaction.setToAccountBalanceBefore(toAccount.getBalance());

        // 7. Procesar
        transaction.process();

        // 8. Ejecutar depósito
        toAccount.deposit(dto.amount());

        // 9. Guardar balance después
        transaction.setToAccountBalanceAfter(toAccount.getBalance());

        // 10. Completar
        transaction.complete();

        // 11. Persistir
        Transaction savedTransaction = transactionRepository.save(transaction);
        accountService.updateBalance(toAccount);

        // 12. Auditar
        auditLogService.logSuccess(
                employee,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Check deposit created"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("toAccount", toAccount.getAccountCode()),
                        new AuditLogDetail("checkNumber", dto.checkNumber()),
                        new AuditLogDetail("bankName", dto.bankName())
                )
        );

        // 13. Publicar evento al outbox
        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, null, toAccount, dto.amount()),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Check deposit completed: {} to {} (Check #{} from {})",
                dto.amount(), toAccount.getAccountCode(), dto.checkNumber(), dto.bankName());

        return transactionMapper.toDto(savedTransaction);
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAS
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    @Override
    public Page<TransactionResponseDto> getMyTransactions(String userEmail, TransactionFiltersDto filters, Pageable pageable) {
        User user = userService.getEntityUserByEmail(userEmail);
        return transactionRepository.findUserTransactions(
                user.getId(),
                filters.type(),
                filters.status(),
                filters.category(),
                filters.startDate(),
                filters.endDate(),
                pageable
        ).map(transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionResponseDto getMyTransaction(UUID transactionId, String userEmail) {
        Transaction transaction = transactionRepository.findByIdWithAccounts(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        boolean isOwner = isTransactionOwner(transaction, userEmail);
        if (!isOwner) {
            throw new UnauthorizedException("You don't have access to this transaction");
        }

        return transactionMapper.toDto(transaction);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<TransactionResponseDto> getAccountTransactions(String accountCode, String userEmail, TransactionFiltersDto filters, Pageable pageable) {
        Account account = accountService.findAccountWithUserByAccountCode(accountCode);

        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("You don't own this account");
        }

        return transactionRepository.findAccountTransactionsByUser(
                accountCode,
                account.getUser().getId(),
                filters.type(),
                filters.status(),
                filters.category(),
                filters.startDate(),
                filters.endDate(),
                pageable
        ).map(transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponseDto> getTransactionsByCategory(TransactionCategory category, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        return transactionRepository.findByCategoryAndUser(category, user.getId())
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CategorySummaryDto getCategorySummary(String userEmail, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userService.getEntityUserByEmail(userEmail);

        List<Transaction> completedTransactions = transactionRepository.findUserTransactions(
                user.getId(),
                null,
                TransactionStatus.COMPLETED,
                null,
                startDate,
                endDate,
                Pageable.unpaged()
        ).getContent();

        Map<TransactionCategory, BigDecimal> totalByCategory = completedTransactions.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        BigDecimal totalSpent = totalByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        TransactionCategory topCategory = totalByCategory.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);

        return new CategorySummaryDto(
                totalByCategory,
                totalSpent,
                topCategory,
                completedTransactions.size()
        );
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ESPECIALES
    // ══════════════════════════════════════════════════════════

    @Transactional
    @Override
    public TransactionResponseDto scheduleTransfer(ScheduledTransferRequestDto dto, String userEmail, TransactionRequestMetadataDto metadata) {
        User user = userService.getEntityUserByEmail(userEmail);

        Account fromAccount = accountService.findAccountWithUserByAccountCode(dto.fromAccountCode());
        Account toAccount = accountService.findAccountWithUserByAccountCode(dto.toAccountCode());

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't own the source account");
        }

        if (dto.fromAccountCode().equals(dto.toAccountCode())) {
            throw new TransactionInvalidException(dto.fromAccountCode(), "Cannot schedule transfer to the same account");
        }

        accountService.validateCanWithdraw(fromAccount, dto.amount());
        accountService.validateCanReceiveDeposit(toAccount);

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.TRANSFER);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description() != null ? dto.description() : "Transferencia programada");
        transaction.setStatus(TransactionStatus.SCHEDULED);
        transaction.setScheduledFor(dto.scheduledFor());
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        transactionMetadataEnricher.enrich(transaction, metadata, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());
        transaction.setToAccountBalanceBefore(toAccount.getBalance());

        Transaction savedTransaction = transactionRepository.save(transaction);

        auditLogService.logSuccess(
                user,
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                savedTransaction.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Scheduled transfer created"),
                        new AuditLogDetail("amount", dto.amount()),
                        new AuditLogDetail("scheduledFor", dto.scheduledFor()),
                        new AuditLogDetail("fromAccount", fromAccount.getAccountCode()),
                        new AuditLogDetail("toAccount", toAccount.getAccountCode())
                )
        );

        log.info("Transfer scheduled: {} from {} to {} at {}",
                dto.amount(), dto.fromAccountCode(), dto.toAccountCode(), dto.scheduledFor());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional
    @Override
    public void cancelScheduledTransaction(UUID transactionId, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        Transaction transaction = transactionRepository.findByIdWithFromAccount(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        boolean ownsFromAccount = transaction.getFromAccount() != null
                && transaction.getFromAccount().getUser().getId().equals(user.getId());

        if (!ownsFromAccount) {
            throw new UnauthorizedException("You don't own this transaction");
        }

        if (transaction.getStatus() != TransactionStatus.SCHEDULED) {
            throw new TransactionStatusException(
                    transaction.getTransactionCode(),
                    transaction.getStatus(),
                    TransactionStatus.CANCELLED
            );
        }

        transaction.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(transaction);

        log.info("Scheduled transaction {} cancelled by {}", transactionId, userEmail);
    }

    @Transactional
    @Override
    public void requestReversal(UUID transactionId, String reason, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        Transaction transaction = transactionRepository.findByIdWithAccounts(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        boolean ownsTransaction = (transaction.getFromAccount() != null
                && transaction.getFromAccount().getUser().getId().equals(user.getId()))
                || (transaction.getToAccount() != null
                && transaction.getToAccount().getUser().getId().equals(user.getId()));

        if (!ownsTransaction) {
            throw new UnauthorizedException("You don't own this transaction");
        }

        if (!transaction.canBeReversed()) {
            throw new TransactionInvalidException(
                    transaction.getTransactionCode(),
                    "Reversal window expired or transaction is not eligible for reversal"
            );
        }

        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new TransactionStatusException(
                    transaction.getTransactionCode(),
                    transaction.getStatus(),
                    TransactionStatus.REVERSED
            );
        }

        transaction.setStatus(TransactionStatus.PENDING_REVIEW);
        transaction.setReversalReason(reason);
        transactionRepository.save(transaction);

        log.info("Reversal requested for transaction {} by {}: {}",
                transactionId, userEmail, reason);
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    @Override
    public Page<TransactionResponseDto> getAllTransactions(TransactionFiltersDto filters, Pageable pageable, String adminEmail) {
        Page<Transaction> transactions = transactionRepository.findAllWithFilters(
                filters.type(),
                filters.status(),
                filters.category(),
                filters.startDate(),
                filters.endDate(),
                filters.accountCode(),
                pageable
        );
        return transactions.map(transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponseDto> getSuspiciousTransactions(String analystEmail) {
        return transactionRepository.findSuspiciousTransactions()
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    @Transactional
    @Override
    public TransactionResponseDto approveTransaction(UUID transactionId, String adminEmail) {
        Transaction transaction = transactionRepository.findByIdWithAccounts(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        if (transaction.getStatus() != TransactionStatus.PENDING_REVIEW) {
            throw new TransactionStatusException(
                    transaction.getTransactionCode(),
                    transaction.getStatus(),
                    TransactionStatus.APPROVED
            );
        }

        transaction.approve(adminEmail);

        // If the transaction was flagged for fraud, the funds were already blocked during
        // the original call. Completing it here moves money and settles the transaction.
        if (transaction.isFlaggedForFraud()) {
            Account fromAccount = transaction.getFromAccount();
            Account toAccount = transaction.getToAccount();
            BigDecimal amount = transaction.getAmount();

            if (fromAccount != null) {
                fromAccount.confirmBlockedFunds(amount);
            }

            if (toAccount != null) {
                toAccount.deposit(amount);
            }

            transaction.completeFromApproved();

            // Update balance-after fields after fund movements
            if (fromAccount != null) {
                transaction.setFromAccountBalanceAfter(fromAccount.getBalance());
            }

            if (toAccount != null) {
                transaction.setToAccountBalanceAfter(toAccount.getBalance());
            }

            if (fromAccount != null) {
                accountService.updateBalance(fromAccount);
            }

            if (toAccount != null) {
                accountService.updateBalance(toAccount);
            }
        }

        Transaction savedTransaction = transactionRepository.save(transaction);

        String eventType = transaction.isFlaggedForFraud() ? "TransactionCompleted" : "TransactionApproved";
        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                eventType,
                buildTransactionPayload(savedTransaction, eventType),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Transaction {} approved by admin {} — status: {}", transactionId, adminEmail, savedTransaction.getStatus());

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional
    @Override
    public void rejectTransaction(UUID transactionId, String reason, String adminEmail) {
        Transaction transaction = transactionRepository.findByIdWithAccounts(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        transaction.setStatus(TransactionStatus.REJECTED);
        transaction.setRejectionReason(reason);
        transactionRepository.save(transaction);

        log.info("Transaction {} rejected by admin {}: {}", transactionId, adminEmail, reason);
    }

    @Transactional
    @Override
    public TransactionResponseDto reverseTransaction(UUID transactionId, String reason, String adminEmail) {
        Transaction transaction = transactionRepository.findByIdWithAccounts(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        if (!transaction.canBeReversed()) {
            throw new TransactionInvalidException(
                    transaction.getTransactionCode(),
                    "Reversal window expired or transaction is not eligible for reversal"
            );
        }

        transaction.reverse(reason);

        if (transaction.getFromAccount() != null) {
            transaction.getFromAccount().deposit(transaction.getAmount());
            accountService.updateBalance(transaction.getFromAccount());
        }

        if (transaction.getToAccount() != null) {
            accountService.validateCanWithdraw(transaction.getToAccount(), transaction.getAmount());
            transaction.getToAccount().withdraw(transaction.getAmount());
            accountService.updateBalance(transaction.getToAccount());
        }

        Transaction savedTransaction = transactionRepository.save(transaction);

        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionReversed",
                buildTransactionPayload(savedTransaction, "TransactionReversed"),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Transaction {} reversed by admin {}: {}", transactionId, adminEmail, reason);

        return transactionMapper.toDto(savedTransaction);
    }

    @Transactional
    @Override
    public void flagAsFraud(UUID transactionId, BigDecimal fraudScore, String reason, String analystEmail) {
        Transaction transaction = transactionRepository.findByIdWithAccounts(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        transaction.flagForFraud(fraudScore, reason);
        transactionRepository.save(transaction);

        log.info("Transaction {} flagged as fraud by analyst {}", transactionId, analystEmail);
        log.debug("Transaction {} fraud details — score={}, reason={}", transactionId, fraudScore, reason);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ══════════════════════════════════════════════════════════

    /**
     * Runs the fraud analysis gate for a persisted transaction.
     * Returns true if the transaction should proceed (CLEAR).
     * Returns false if SUSPICIOUS (transaction flagged for review, caller must return early).
     * Throws FraudBlockedException if BLOCKED after persisting failure trace and outbox event.
     */
    private boolean executeFraudGate(Transaction savedTx, Account fromAccount, BigDecimal amount) {
        TransactionFraudContext context = new TransactionFraudContext(
                savedTx.getId() != null ? savedTx.getId().toString() : null,
                fromAccount.getAccountCode(),
                savedTx.getToAccount() != null ? savedTx.getToAccount().getAccountCode() : null,
                amount,
                savedTx.getCurrency() != null ? savedTx.getCurrency() : "CRC",
                savedTx.getTransactionCode(),
                savedTx.getType() != null ? savedTx.getType().name() : null,
                savedTx.getChannel() != null ? savedTx.getChannel().name() : null,
                savedTx.getMerchantName(),
                savedTx.getMerchantMccCode(),
                savedTx.getIpAddress(),
                savedTx.getDeviceId(),
                savedTx.getLocationCountry()
        );

        FraudAnalysisResult result = fraudDetectionService.analyze(context);

        return switch (result) {
            case CLEAR -> true;
            case SUSPICIOUS -> {
                savedTx.flagForFraud(SUSPICIOUS_FRAUD_SCORE, "Suspicious activity detected by fraud engine");
                transactionRepository.save(savedTx);
                outboxEventPort.save(new OutboxEvent(
                        "Transaction",
                        savedTx.getId().toString(),
                        "TransactionFlaggedForReview",
                        buildTransactionPayload(savedTx, "TransactionFlaggedForReview"),
                        KafkaTopic.TRANSACTION_EVENTS
                ));
                yield false;
            }
            case BLOCKED -> {
                fromAccount.unblockFunds(amount);
                savedTx.fail("Blocked by fraud detection engine");
                transactionRepository.save(savedTx);
                outboxEventPort.save(new OutboxEvent(
                        "Transaction",
                        savedTx.getId().toString(),
                        "TransactionFraudBlocked",
                        buildTransactionPayload(savedTx, "TransactionFraudBlocked"),
                        KafkaTopic.TRANSACTION_EVENTS
                ));
                throw new FraudBlockedException(savedTx.getTransactionCode(), "Blocked by fraud detection engine");
            }
        };
    }

    private boolean isTransactionOwner(Transaction transaction, String userEmail) {
        boolean isFromOwner = transaction.getFromAccount() != null
                && transaction.getFromAccount().getUser().getEmail().equals(userEmail);
        boolean isToOwner = transaction.getToAccount() != null
                && transaction.getToAccount().getUser().getEmail().equals(userEmail);
        return isFromOwner || isToOwner;
    }

    private String buildTransactionPayload(Transaction transaction, String eventType) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", eventType);
            payload.put("transactionId", transaction.getId().toString());
            payload.put("transactionCode", transaction.getTransactionCode());
            payload.put("type", transaction.getType() != null ? transaction.getType().name() : null);
            payload.put("fromAccountCode", transaction.getFromAccount() != null
                    ? transaction.getFromAccount().getAccountCode() : null);
            payload.put("toAccountCode", transaction.getToAccount() != null
                    ? transaction.getToAccount().getAccountCode() : null);
            payload.put("amount", transaction.getAmount());
            payload.put("currency", transaction.getCurrency());
            payload.put("status", transaction.getStatus() != null ? transaction.getStatus().name() : null);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }

    private String buildTransactionPayload(Transaction transaction, Account fromAccount, Account toAccount, BigDecimal amount) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "TransactionCompleted");
            payload.put("transactionId", transaction.getId().toString());
            payload.put("fromAccount", fromAccount != null ? fromAccount.getAccountCode() : null);
            payload.put("toAccount", toAccount != null ? toAccount.getAccountCode() : null);
            payload.put("amount", amount);
            payload.put("currency", transaction.getCurrency());
            payload.put("status", transaction.getStatus() != null ? transaction.getStatus().name() : null);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
