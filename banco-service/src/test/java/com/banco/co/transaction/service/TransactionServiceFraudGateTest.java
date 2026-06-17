package com.banco.co.transaction.service;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.card.service.ICardService;
import com.banco.co.exception.fraud.FraudBlockedException;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.service.IFraudDetectionService;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.transaction.domain.model.Transaction;
import com.banco.co.transaction.domain.port.out.ITransactionRepository;
import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.TransferRequestDto;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.exception.transaction.TransactionNotFoundException;
import com.banco.co.transaction.exception.transaction.TransactionStatusException;
import com.banco.co.transaction.mapper.ITransactionMapper;
import com.banco.co.transaction.utils.metadata.ITransactionMetadataEnricher;
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.domain.port.out.IUserRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-layer tests for TransactionService fraud gate paths.
 * Covers: transfer() CLEAR/SUSPICIOUS/BLOCKED + approveTransaction() flagged/not-flagged/wrong-status.
 *
 * BLOCKED path note:
 *   transfer/payment/payService/cashWithdrawal now use @Transactional(noRollbackFor = FraudBlockedException.class)
 *   so FAILED status and TransactionFraudBlocked outbox event are expected to persist.
 *   In this unit test we verify the exception and state transitions at service level.
 *
 * Phase 3 migration note:
 *   TransactionService now injects IAccountUseCase (domain port) instead of IAccountService.
 *   Ownership check uses account.getUserId() instead of account.getUser().getId().
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceFraudGateTest {

    @Mock private IAccountUseCase accountUseCase;
    @Mock private ITransactionRepository transactionRepository;
    @Mock private IUserService userService;
    @Mock private IAuditLogService auditLogService;
    @Mock private ITransactionMapper transactionMapper;
    @Mock private ITransactionMetadataEnricher transactionMetadataEnricher;
    @Mock private IOutboxEventPort outboxEventPort;
    @Mock private ICardService cardService;
    @Mock private IFraudDetectionService fraudDetectionService;
    @Mock private IUserRepository userDomainRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        testUser = buildUser("user@banco.co");

        fromAccount = buildDomainAccount("ACC-FROM-001", new BigDecimal("1000000"), testUser.getId());
        toAccount   = buildDomainAccount("ACC-TO-001",   new BigDecimal("500000"),  UUID.randomUUID());
    }

    // ══════════════════════════════════════════════════════════
    //  transfer() — CLEAR path
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransfer_FraudClear_CompletesAndPublishesTransactionCompleted() {
        TransferRequestDto dto = buildTransferDto();
        TransactionResponseDto expectedResponse = buildResponseDto("TXN-BCR-001");

        stubTransferCommonMocks(expectedResponse);
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.CLEAR);
        stubSnapshotForAccount(fromAccount);
        stubSnapshotForAccount(toAccount);

        TransactionResponseDto result = transactionService.transfer(dto, "user@banco.co", buildMetadata());

        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).getEventType()).isEqualTo("TransactionCompleted");
    }

    // ══════════════════════════════════════════════════════════
    //  transfer() — SUSPICIOUS path
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransfer_FraudSuspicious_ReturnsPendingReviewAndPublishesFlaggedEvent() {
        TransferRequestDto dto = buildTransferDto();
        TransactionResponseDto expectedResponse = buildResponseDto("TXN-BCR-002");

        stubTransferCommonMocks(expectedResponse);
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.SUSPICIOUS);

        TransactionResponseDto result = transactionService.transfer(dto, "user@banco.co", buildMetadata());

        assertThat(result).isEqualTo(expectedResponse);

        // Flagged event published
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("TransactionFlaggedForReview");

        // In SUSPICIOUS path: initial save + flag-update save = 2 total saves
        verify(transactionRepository, times(2)).save(any(Transaction.class));

        // SUSPICIOUS returns early — toAccount balance must NOT be updated
        verify(accountUseCase, never()).updateBalance(toAccount);
    }

    // ══════════════════════════════════════════════════════════
    //  transfer() — BLOCKED path
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransfer_FraudBlocked_ThrowsFraudBlockedException() {
        TransferRequestDto dto = buildTransferDto();

        stubTransferCommonMocksBlocked();
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.BLOCKED);

        assertThatThrownBy(() -> transactionService.transfer(dto, "user@banco.co", buildMetadata()))
                .isInstanceOf(FraudBlockedException.class);
    }

    @Test
    void testTransfer_FraudBlocked_SaveIsCalledForFailedStatusBeforeRollback() {
        // Verifies initial save + failed-status save attempt in BLOCKED path.
        TransferRequestDto dto = buildTransferDto();

        stubTransferCommonMocksBlocked();
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.BLOCKED);

        try {
            transactionService.transfer(dto, "user@banco.co", buildMetadata());
        } catch (FraudBlockedException ignored) {
            // Expected
        }

        // Initial save (before fraud gate) + BLOCKED save attempt
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void testTransfer_FraudBlocked_TransactionStatusIsFailedInMemory() {
        // Confirms the in-memory state after BLOCKED path: fail() was called on the transaction.
        TransferRequestDto dto = buildTransferDto();

        // Use a real Transaction so we can inspect in-memory state after the call
        Transaction realSavedTx = buildSavedTransaction("TXN-BCR-BLOCKED", TransactionStatus.PROCESSING);

        stubTransferCommonMocksWithRealTx(realSavedTx);
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.BLOCKED);

        try {
            transactionService.transfer(dto, "user@banco.co", buildMetadata());
        } catch (FraudBlockedException ignored) {
            // Expected
        }

        assertThat(realSavedTx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    // ══════════════════════════════════════════════════════════
    //  approveTransaction() — flagged for fraud path
    // ══════════════════════════════════════════════════════════

    @Test
    void testApproveTransaction_FlaggedForFraud_ExecutesFundMovementAndCompletesTransaction() {
        UUID txId = UUID.randomUUID();
        Transaction flaggedTx = buildFlaggedTransaction(fromAccount, toAccount);
        TransactionResponseDto expectedResponse = buildResponseDto("TXN-BCR-FLAGGED");

        when(transactionRepository.findByIdWithAccounts(txId)).thenReturn(Optional.of(flaggedTx));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(flaggedTx);
        when(transactionMapper.toDto(any())).thenReturn(expectedResponse);
        // Service now loads accounts by ID for flagged approval via IAccountUseCase
        when(accountUseCase.getAccountById(fromAccount.getId())).thenReturn(fromAccount);
        when(accountUseCase.getAccountById(toAccount.getId())).thenReturn(toAccount);
        stubSnapshotForAccount(fromAccount);
        stubSnapshotForAccount(toAccount);

        TransactionResponseDto result = transactionService.approveTransaction(txId, "admin@banco.co");

        assertThat(result).isEqualTo(expectedResponse);

        // Fund movement must have happened for both accounts
        verify(accountUseCase).updateBalance(fromAccount);
        verify(accountUseCase).updateBalance(toAccount);

        // Transaction is COMPLETED after flagged approval
        assertThat(flaggedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // approveTransaction flagged path emits domain event + notification event
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).getEventType()).isEqualTo("TransactionCompleted");
    }

    // ══════════════════════════════════════════════════════════
    //  approveTransaction() — not flagged for fraud path
    // ══════════════════════════════════════════════════════════

    @Test
    void testApproveTransaction_NotFlaggedForFraud_ApprovesWithoutFundMovement() {
        UUID txId = UUID.randomUUID();
        Transaction nonFlaggedTx = buildNonFlaggedTransaction();
        TransactionResponseDto expectedResponse = buildResponseDto("TXN-BCR-NOTFLAGGED");

        when(transactionRepository.findByIdWithAccounts(txId)).thenReturn(Optional.of(nonFlaggedTx));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(nonFlaggedTx);
        when(transactionMapper.toDto(any())).thenReturn(expectedResponse);

        transactionService.approveTransaction(txId, "admin@banco.co");

        // No fund movement for non-flagged approval
        verify(accountUseCase, never()).updateBalance(any());

        // Outbox event must be TransactionApproved for regular review path
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("TransactionApproved");
    }

    // ══════════════════════════════════════════════════════════
    //  approveTransaction() — wrong status guard
    // ══════════════════════════════════════════════════════════

    @Test
    void testApproveTransaction_WhenStatusIsCompleted_ThrowsTransactionStatusException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setTransactionCode("TXN-BCR-DONE");

        when(transactionRepository.findByIdWithAccounts(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.approveTransaction(txId, "admin@banco.co"))
                .isInstanceOf(TransactionStatusException.class);
    }

    @Test
    void testApproveTransaction_WhenStatusIsPending_ThrowsTransactionStatusException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PENDING);
        tx.setTransactionCode("TXN-BCR-PENDING");

        when(transactionRepository.findByIdWithAccounts(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.approveTransaction(txId, "admin@banco.co"))
                .isInstanceOf(TransactionStatusException.class);
    }

    @Test
    void testApproveTransaction_WhenTransactionNotFound_ThrowsTransactionNotFoundException() {
        UUID txId = UUID.randomUUID();

        when(transactionRepository.findByIdWithAccounts(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.approveTransaction(txId, "admin@banco.co"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  Phase 3 — ownership check uses getUserId() not getUser().getId()
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransfer_AccountOwnedByOtherUser_ThrowsUnauthorizedException() {
        // Domain Account with foreign userId — ownership check must fail
        Account foreignAccount = buildDomainAccount("ACC-FROM-001", new BigDecimal("1000000"), UUID.randomUUID());

        when(userService.getEntityUserByEmail("user@banco.co")).thenReturn(testUser);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(foreignAccount);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);

        TransferRequestDto dto = buildTransferDto();

        assertThatThrownBy(() -> transactionService.transfer(dto, "user@banco.co", buildMetadata()))
                .isInstanceOf(com.banco.co.exception.authentication.UnauthorizedException.class);

        // Card save must never be reached
        verify(transactionRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════
    //  Stub helpers
    // ══════════════════════════════════════════════════════════

    private void stubTransferCommonMocks(TransactionResponseDto response) {
        when(userService.getEntityUserByEmail("user@banco.co")).thenReturn(testUser);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(fromAccount);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);
        doNothing().when(accountUseCase).validateCanWithdraw(any(), any());
        doNothing().when(accountUseCase).validateCanReceiveDeposit(any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(
                buildSavedTransaction("TXN-BCR-001", TransactionStatus.PROCESSING));
        when(transactionMapper.toDto(any())).thenReturn(response);
    }

    private void stubTransferCommonMocksBlocked() {
        when(userService.getEntityUserByEmail("user@banco.co")).thenReturn(testUser);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(fromAccount);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);
        doNothing().when(accountUseCase).validateCanWithdraw(any(), any());
        doNothing().when(accountUseCase).validateCanReceiveDeposit(any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(
                buildSavedTransaction("TXN-BCR-003", TransactionStatus.PROCESSING));
    }

    private void stubTransferCommonMocksWithRealTx(Transaction realTx) {
        when(userService.getEntityUserByEmail("user@banco.co")).thenReturn(testUser);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(fromAccount);
        when(accountUseCase.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);
        doNothing().when(accountUseCase).validateCanWithdraw(any(), any());
        doNothing().when(accountUseCase).validateCanReceiveDeposit(any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(realTx);
    }

    private void stubSnapshotForAccount(Account account) {
        UserSnapshot snapshot = new UserSnapshot(
                account.getUserId().toString(),
                "user@banco.co",
                "TestUser",
                "USER"
        );
        when(userDomainRepository.findSnapshotByUserId(account.getUserId())).thenReturn(snapshot);
    }

    // ══════════════════════════════════════════════════════════
    //  Object builders
    // ══════════════════════════════════════════════════════════

    private TransferRequestDto buildTransferDto() {
        return new TransferRequestDto(
                "ACC-FROM-001", "ACC-TO-001", new BigDecimal("100000"), "Test transfer", true);
    }

    private TransactionRequestMetadataDto buildMetadata() {
        return new TransactionRequestMetadataDto(null, null, null);
    }

    private TransactionResponseDto buildResponseDto(String code) {
        return new TransactionResponseDto(
                code,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Builds a domain Account (com.banco.co.account.domain.model.Account) with userId set.
     * Phase 3: no legacy Account.setUser() — uses domain Account.setUserId().
     */
    private Account buildDomainAccount(String accountCode, BigDecimal balance, UUID userId) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountCode(accountCode);
        account.setUserId(userId);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.SAVINGS);
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            account.deposit(balance);
        }
        return account;
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }

    private Transaction buildSavedTransaction(String code, TransactionStatus status) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setTransactionCode(code);
        tx.setStatus(status);
        tx.setCurrency("CRC");
        tx.setAmount(new BigDecimal("100000"));
        tx.setFromAccountId(fromAccount.getId());
        tx.setFromAccountCode(fromAccount.getAccountCode());
        tx.setToAccountId(toAccount.getId());
        tx.setToAccountCode(toAccount.getAccountCode());
        tx.setType(com.banco.co.transaction.enums.TransactionType.TRANSFER);
        return tx;
    }

    private Transaction buildFlaggedTransaction(Account from, Account to) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setTransactionCode("TXN-BCR-FLAGGED");
        // flagForFraud sets status to PENDING_REVIEW (required by approveTransaction)
        tx.flagForFraud(BigDecimal.valueOf(75), "Suspicious activity detected");
        tx.setFromAccountId(from.getId());
        tx.setFromAccountCode(from.getAccountCode());
        tx.setToAccountId(to.getId());
        tx.setToAccountCode(to.getAccountCode());
        tx.setAmount(new BigDecimal("100000"));
        tx.setCurrency("CRC");
        tx.setType(com.banco.co.transaction.enums.TransactionType.TRANSFER);
        from.blockFunds(tx.getAmount());
        return tx;
    }

    private Transaction buildNonFlaggedTransaction() {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setTransactionCode("TXN-BCR-NOTFLAGGED");
        // Non-flagged: status starts at PENDING_REVIEW (required by approveTransaction)
        tx.setStatus(TransactionStatus.PENDING_REVIEW);
        tx.setAmount(new BigDecimal("100000"));
        tx.setCurrency("CRC");
        return tx;
    }
}
