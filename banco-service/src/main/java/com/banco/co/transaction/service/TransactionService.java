package com.banco.co.transaction.service;

import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
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
import com.banco.co.transaction.mapper.ITransactionMapper;
import com.banco.co.transaction.utils.metadata.ITransactionMetadataEnricher;
import com.banco.co.transaction.model.Transaction;
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
public class TransactionService implements ITransactionService{
    private final IAccountService accountService;
    private final ITransactionRepository transactionRepository;
    private final IUserService userService;
    private final IAuditLogService auditLogService;
    private final ITransactionMapper transactionMapper;
    private final ITransactionMetadataEnricher transactionMetadataEnricher;
    private final IOutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;
    @Transactional
    @Override
    public TransactionResponseDto transfer(TransferRequestDto dto, String userEmail, HttpServletRequest request) {
        // 1. Validar usuario
        User user = userService.getEntityUserByEmail(userEmail);

        // 2. Obtener cuentas
        Account fromAccount = accountService.findAccountWithUserByAccountCode(dto.fromAccountCode());
        Account toAccount = accountService.findAccountWithUserByAccountCode(dto.toAccountCode());

        // 3. Validar ownership de cuenta origen
        if (!fromAccount.getUser().getId().equals(user.getId())) {

            auditLogService.logFailure(
                    user,
                    AuditAction.TRANSACTION_FAILED,
                    AuditEntityType.TRANSACTION,
                    List.of(
                            new AuditLogDetail("message", "Attempted transfer from account not owned")
                    )
            );

            throw new UnauthorizedException("You don't own the source account");
        }

        // 4. Validar que no sea la misma cuenta
        if (dto.fromAccountCode().equals(dto.toAccountCode())) {
            throw new TransactionInvalidException(dto.fromAccountCode(),"Cannot transfer to the same account");
        }

        // 5. Validar fondos
        accountService.validateCanWithdraw(fromAccount, dto.amount());

        // 6. Validar cuenta destino puede recibir
        accountService.validateCanReceiveDeposit(toAccount);

        // 7. Crear transacción
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.TRANSFER);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(dto.amount());
        transaction.setDescription(
                dto.description() != null ? dto.description() : "Transferencia"
        );
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrency("CRC");
        transaction.setFee(BigDecimal.ZERO);
        transaction.setNetAmount(dto.amount());

        // 8. Enriquecer metadata automáticamente
        transactionMetadataEnricher.enrich(transaction, request, TransactionChannel.WEB);

        // 9. Guardar balances antes
        transaction.setFromAccountBalanceBefore(fromAccount.getBalance());
        transaction.setToAccountBalanceBefore(toAccount.getBalance());

        // 10. Procesar
        transaction.process();

        // 11. Ejecutar transferencia
        fromAccount.blockFunds(dto.amount());
        toAccount.blockFunds(dto.amount());

        // 12. Guardar balances después
        transaction.setFromAccountBalanceAfter(fromAccount.getBalance());
        transaction.setToAccountBalanceAfter(toAccount.getBalance());

        // 13. Completar
        transaction.complete();

        // 14. Guardar todo
        Transaction savedTransaction = transactionRepository.save(transaction);
        accountService.updateBalance(fromAccount);
        accountService.updateBalance(toAccount);

        // 15. Auditar
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

        // 16. Publicar evento al outbox (misma transacción DB)
        outboxEventPort.save(new OutboxEvent(
                "Transaction",
                savedTransaction.getId().toString(),
                "TransactionCompleted",
                buildTransactionPayload(savedTransaction, fromAccount, toAccount, dto.amount()),
                KafkaTopic.TRANSACTION_EVENTS
        ));

        log.info("Transfer completed: {} from {} to {}",
                dto.amount(), fromAccount.getAccountCode(), toAccount.getAccountCode());

        return transactionMapper.toDto(savedTransaction);
    }

    @Override
    public TransactionResponseDto payment(PaymentRequestDto dto, String userEmail, HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public TransactionResponseDto payService(ServicePaymentRequestDto dto, String userEmail, HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
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

    @Override
    public TransactionResponseDto scheduleTransfer(ScheduledTransferRequestDto dto, String userEmail, HttpServletRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void cancelScheduledTransaction(UUID transactionId, String userEmail) {

    }

    @Override
    public void requestReversal(UUID transactionId, String reason, String userEmail) {

    }

    @Transactional(readOnly = true)
    @Override
    public Page<TransactionResponseDto> getAllTransactions(TransactionFiltersDto filters, Pageable pageable, String adminEmail) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    @Override
    public List<TransactionResponseDto> getSuspiciousTransactions(String analystEmail) {
        return List.of();
    }

    @Override
    public TransactionResponseDto approveTransaction(UUID transactionId, String adminEmail) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void rejectTransaction(UUID transactionId, String reason, String adminEmail) {

    }

    @Override
    public TransactionResponseDto reverseTransaction(UUID transactionId, String reason, String adminEmail) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void flagAsFraud(UUID transactionId, BigDecimal fraudScore, String reason, String analystEmail) {

    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ══════════════════════════════════════════════════════════

    private String buildTransactionPayload(Transaction transaction, Account fromAccount, Account toAccount, BigDecimal amount) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "TransactionCompleted");
            payload.put("transactionId", transaction.getId().toString());
            payload.put("fromAccount", fromAccount.getAccountCode());
            payload.put("toAccount", toAccount.getAccountCode());
            payload.put("amount", amount);
            payload.put("currency", transaction.getCurrency());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
