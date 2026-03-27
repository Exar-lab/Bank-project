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
import com.banco.co.transaction.dto.CategorySummaryDto;
import com.banco.co.transaction.dto.ScheduledTransferRequestDto;
import com.banco.co.transaction.dto.TransactionFiltersDto;
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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES DIGITALES
    // ══════════════════════════════════════════════════════════

    @Transactional
    @Override
    public TransactionResponseDto transfer(TransferRequestDto dto, String userEmail, HttpServletRequest request) {
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

        transactionMetadataEnricher.enrich(transaction, request, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());
        transaction.setToAccountBalanceBefore(toAccount.getBalance());

        transaction.process();

        fromAccount.blockFunds(dto.amount());
        toAccount.blockFunds(dto.amount());

        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());
        transaction.setToAccountBalanceAfter(toAccount.getBalance());

        transaction.complete();

        Transaction savedTransaction = transactionRepository.save(transaction);
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

    @Transactional
    @Override
    public TransactionResponseDto payment(PaymentRequestDto dto, String userEmail, HttpServletRequest request) {
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

        transactionMetadataEnricher.enrich(transaction, request, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());

        transaction.process();

        fromAccount.blockFunds(dto.amount());
        card.recordTransaction(dto.amount(), TransactionType.PAYMENT);

        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());

        transaction.complete();

        Transaction savedTransaction = transactionRepository.save(transaction);
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

    @Transactional
    @Override
    public TransactionResponseDto payService(ServicePaymentRequestDto dto, String userEmail, HttpServletRequest request) {
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

        transactionMetadataEnricher.enrich(transaction, request, TransactionChannel.WEB);

        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());

        transaction.process();

        fromAccount.blockFunds(dto.amount());

        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());

        transaction.complete();

        Transaction savedTransaction = transactionRepository.save(transaction);
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

    @Override
    public TransactionResponseDto cashDeposit(CashDepositRequestDto dto, String employeeEmail, HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public TransactionResponseDto cashWithdrawal(CashWithdrawalRequestDto dto, String employeeEmail, HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public TransactionResponseDto checkDeposit(CheckDepositRequestDto dto, String employeeEmail, HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAS
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    @Override
    public Page<TransactionResponseDto> getMyTransactions(String userEmail, TransactionFiltersDto filters, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionResponseDto getMyTransaction(UUID transactionId, String userEmail) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    @Override
    public Page<TransactionResponseDto> getAccountTransactions(String accountCode, String userEmail, TransactionFiltersDto filters, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponseDto> getTransactionsByCategory(TransactionCategory category, String userEmail) {
        return List.of();
    }

    @Transactional(readOnly = true)
    @Override
    public CategorySummaryDto getCategorySummary(String userEmail, LocalDateTime startDate, LocalDateTime endDate) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ESPECIALES
    // ══════════════════════════════════════════════════════════

    @Transactional
    @Override
    public TransactionResponseDto scheduleTransfer(ScheduledTransferRequestDto dto, String userEmail, HttpServletRequest request) {
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

        transactionMetadataEnricher.enrich(transaction, request, TransactionChannel.WEB);

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

        transaction.approve(adminEmail);

        Transaction savedTransaction = transactionRepository.save(transaction);

        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionApproved",
                buildTransactionPayload(savedTransaction, "TransactionApproved"),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Transaction {} approved by admin {}", transactionId, adminEmail);

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
            transaction.getToAccount().blockFunds(transaction.getAmount());
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

        log.info("Transaction {} flagged as fraud by analyst {} with score {}: {}",
                transactionId, analystEmail, fraudScore, reason);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ══════════════════════════════════════════════════════════

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
}
