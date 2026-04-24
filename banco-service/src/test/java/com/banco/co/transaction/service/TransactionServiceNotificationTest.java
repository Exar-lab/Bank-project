package com.banco.co.transaction.service;

import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.card.model.Card;
import com.banco.co.card.service.ICardService;
import com.banco.co.exception.fraud.FraudBlockedException;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.service.IFraudDetectionService;
import com.banco.co.outbox.enums.KafkaTopic;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.CashDepositRequestDto;
import com.banco.co.transaction.dto.movement.CashWithdrawalRequestDto;
import com.banco.co.transaction.dto.payment.PaymentRequestDto;
import com.banco.co.transaction.dto.movement.TransferRequestDto;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.transaction.mapper.ITransactionMapper;
import com.banco.co.transaction.model.Transaction;
import com.banco.co.transaction.repository.ITransactionRepository;
import com.banco.co.transaction.utils.metadata.ITransactionMetadataEnricher;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-layer tests for notification outbox events emitted by TransactionService.
 * Covers: transfer/cashDeposit/cashWithdrawal/payment emit 2nd NOTIFICATION event on COMPLETED;
 * reverseTransaction and BLOCKED fraud emit NO notification event.
 * TS-001 through TS-006.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceNotificationTest {

    @Mock private IAccountService accountService;
    @Mock private ITransactionRepository transactionRepository;
    @Mock private IUserService userService;
    @Mock private IAuditLogService auditLogService;
    @Mock private ITransactionMapper transactionMapper;
    @Mock private ITransactionMetadataEnricher transactionMetadataEnricher;
    @Mock private IOutboxEventPort outboxEventPort;
    @Mock private ICardService cardService;
    @Mock private IFraudDetectionService fraudDetectionService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransactionService transactionService;

    private User senderUser;
    private User receiverUser;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        senderUser   = buildUser("sender@banco.co",   "Ana");
        receiverUser = buildUser("receiver@banco.co", "Pedro");
        fromAccount  = buildAccount("ACC-FROM-001", new BigDecimal("1000000"), senderUser);
        toAccount    = buildAccount("ACC-TO-001",   new BigDecimal("500000"),  receiverUser);
    }

    // ══════════════════════════════════════════════════════════
    //  TS-001 — transfer CLEAR → 2 outbox saves, 2nd is NOTIFICATION
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransfer_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents() throws Exception {
        TransferRequestDto dto = new TransferRequestDto(
                "ACC-FROM-001", "ACC-TO-001", new BigDecimal("100000"), "Test", true);
        Transaction savedTx = buildSavedTx("TXN-BCR-001", TransactionStatus.PROCESSING, fromAccount, toAccount, TransactionType.TRANSFER);

        stubTransferMocks(savedTx);
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.CLEAR);

        transactionService.transfer(dto, "sender@banco.co", buildMetadata());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(2)).save(captor.capture());

        List<OutboxEvent> events = captor.getAllValues();
        assertThat(events.get(0).getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_EVENTS);
        assertThat(events.get(1).getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_NOTIFICATION_EVENTS);

        JsonNode payload = objectMapper.readTree(events.get(1).getPayload());
        assertThat(payload.path("eventType").asText()).isEqualTo("TransactionCompletedNotification");
        assertThat(payload.path("fromAccount").path("userEmail").asText()).isEqualTo("sender@banco.co");
        assertThat(payload.path("toAccount").path("userEmail").asText()).isEqualTo("receiver@banco.co");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-002 — cashDeposit → 2 outbox saves, fromAccount=null in notification
    // ══════════════════════════════════════════════════════════

    @Test
    void testCashDeposit_Completed_EmitsDomainAndNotificationOutboxEvents() throws Exception {
        CashDepositRequestDto dto = new CashDepositRequestDto(
                "ACC-TO-001", new BigDecimal("50000"), null, null);
        User employee = buildUser("employee@banco.co", "Carlos");
        Transaction savedTx = buildSavedTx("TXN-BCR-DEP-001", TransactionStatus.COMPLETED, null, toAccount, TransactionType.DEPOSIT);

        when(userService.getEntityUserByEmail("employee@banco.co")).thenReturn(employee);
        when(accountService.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);
        doNothing().when(accountService).validateCanReceiveDeposit(any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(transactionMapper.toDto(any())).thenReturn(buildResponseDto("TXN-BCR-DEP-001"));

        transactionService.cashDeposit(dto, "employee@banco.co", buildMetadata());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(2)).save(captor.capture());

        OutboxEvent notificationEvent = captor.getAllValues().get(1);
        assertThat(notificationEvent.getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_NOTIFICATION_EVENTS);

        JsonNode payload = objectMapper.readTree(notificationEvent.getPayload());
        assertThat(payload.path("fromAccount").isNull()).isTrue();
        assertThat(payload.path("toAccount").path("userEmail").asText()).isEqualTo("receiver@banco.co");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-003 — cashWithdrawal CLEAR → 2 outbox saves, toAccount=null in notification
    // ══════════════════════════════════════════════════════════

    @Test
    void testCashWithdrawal_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents() throws Exception {
        CashWithdrawalRequestDto dto = new CashWithdrawalRequestDto(
                "ACC-FROM-001", new BigDecimal("50000"), null, true, null);
        User employee = buildUser("employee@banco.co", "Carlos");
        Transaction savedTx = buildSavedTx("TXN-BCR-WD-001", TransactionStatus.PROCESSING, fromAccount, null, TransactionType.WITHDRAWAL);

        when(userService.getEntityUserByEmail("employee@banco.co")).thenReturn(employee);
        when(accountService.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(fromAccount);
        doNothing().when(accountService).validateCanWithdraw(any(), any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(transactionMapper.toDto(any())).thenReturn(buildResponseDto("TXN-BCR-WD-001"));
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.CLEAR);

        transactionService.cashWithdrawal(dto, "employee@banco.co", buildMetadata());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(2)).save(captor.capture());

        OutboxEvent notificationEvent = captor.getAllValues().get(1);
        assertThat(notificationEvent.getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_NOTIFICATION_EVENTS);

        JsonNode payload = objectMapper.readTree(notificationEvent.getPayload());
        assertThat(payload.path("toAccount").isNull()).isTrue();
        assertThat(payload.path("fromAccount").path("userEmail").asText()).isEqualTo("sender@banco.co");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-004 — payment CLEAR → 2 outbox saves, toAccount=null in notification
    // ══════════════════════════════════════════════════════════

    @Test
    void testPayment_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents() throws Exception {
        PaymentRequestDto dto = new PaymentRequestDto(
                "CARD-001", new BigDecimal("30000"), "MerchantX", null, null);
        Transaction savedTx = buildSavedTx("TXN-BCR-PAY-001", TransactionStatus.PROCESSING, fromAccount, null, TransactionType.PAYMENT);

        Card card = mock(Card.class);
        when(card.getAccount()).thenReturn(fromAccount);
        when(card.canTransact(any())).thenReturn(true);
        when(card.getCardNumber()).thenReturn("1234567890123456");

        when(userService.getEntityUserByEmail("sender@banco.co")).thenReturn(senderUser);
        when(cardService.findCardWithAccountByCardCode("CARD-001")).thenReturn(card);
        doNothing().when(accountService).validateCanWithdraw(any(), any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(transactionMapper.toDto(any())).thenReturn(buildResponseDto("TXN-BCR-PAY-001"));
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.CLEAR);

        transactionService.payment(dto, "sender@banco.co", buildMetadata());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(2)).save(captor.capture());

        OutboxEvent notificationEvent = captor.getAllValues().get(1);
        assertThat(notificationEvent.getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_NOTIFICATION_EVENTS);

        JsonNode payload = objectMapper.readTree(notificationEvent.getPayload());
        assertThat(payload.path("toAccount").isNull()).isTrue();
        assertThat(payload.path("fromAccount").path("userEmail").asText()).isEqualTo("sender@banco.co");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-005 — reverseTransaction → 1 outbox save, NOTIFICATION never used
    // ══════════════════════════════════════════════════════════

    @Test
    void testReverseTransaction_DoesNotEmitNotificationEvent() {
        UUID txId = UUID.randomUUID();
        Transaction completedTx = buildReversibleTransaction(fromAccount, toAccount);

        when(transactionRepository.findByIdWithAccounts(txId)).thenReturn(Optional.of(completedTx));
        when(transactionRepository.save(any())).thenReturn(completedTx);
        when(transactionMapper.toDto(any())).thenReturn(buildResponseDto("TXN-BCR-REV-001"));

        transactionService.reverseTransaction(txId, "Refund", "admin@banco.co");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_EVENTS);
        assertThat(captor.getValue().getEventType()).isEqualTo("TransactionReversed");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-006 — BLOCKED fraud → 1 outbox save, NOTIFICATION never used
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransfer_FraudBlocked_DoesNotEmitNotificationEvent() {
        TransferRequestDto dto = new TransferRequestDto(
                "ACC-FROM-001", "ACC-TO-001", new BigDecimal("100000"), "Test", true);
        Transaction savedTx = buildSavedTx("TXN-BCR-BLK-001", TransactionStatus.PROCESSING, fromAccount, toAccount, TransactionType.TRANSFER);

        stubTransferMocksBlocked(savedTx);
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.BLOCKED);

        assertThatThrownBy(() -> transactionService.transfer(dto, "sender@banco.co", buildMetadata()))
                .isInstanceOf(FraudBlockedException.class);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().getKafkaTopic()).isEqualTo(KafkaTopic.TRANSACTION_EVENTS);
        assertThat(captor.getValue().getEventType()).isEqualTo("TransactionFraudBlocked");
    }

    // ══════════════════════════════════════════════════════════
    //  Stub helpers
    // ══════════════════════════════════════════════════════════

    private void stubTransferMocks(Transaction savedTx) {
        when(userService.getEntityUserByEmail("sender@banco.co")).thenReturn(senderUser);
        when(accountService.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(fromAccount);
        when(accountService.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);
        doNothing().when(accountService).validateCanWithdraw(any(), any());
        doNothing().when(accountService).validateCanReceiveDeposit(any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(savedTx);
        when(transactionMapper.toDto(any())).thenReturn(buildResponseDto(savedTx.getTransactionCode()));
    }

    private void stubTransferMocksBlocked(Transaction savedTx) {
        when(userService.getEntityUserByEmail("sender@banco.co")).thenReturn(senderUser);
        when(accountService.findAccountWithUserByAccountCode("ACC-FROM-001")).thenReturn(fromAccount);
        when(accountService.findAccountWithUserByAccountCode("ACC-TO-001")).thenReturn(toAccount);
        doNothing().when(accountService).validateCanWithdraw(any(), any());
        doNothing().when(accountService).validateCanReceiveDeposit(any());
        doNothing().when(transactionMetadataEnricher).enrich(any(), any(), any());
        when(transactionRepository.save(any())).thenReturn(savedTx);
    }

    // ══════════════════════════════════════════════════════════
    //  Object builders
    // ══════════════════════════════════════════════════════════

    private User buildUser(String email, String firstName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFistName(firstName);
        return user;
    }

    private Account buildAccount(String accountCode, BigDecimal balance, User user) {
        Account account = new Account();
        account.setUser(user);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.SAVINGS);
        try {
            var field = Account.class.getDeclaredField("accountCode");
            field.setAccessible(true);
            field.set(account, accountCode);
        } catch (Exception e) {
            throw new IllegalStateException("Could not set accountCode", e);
        }
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            account.deposit(balance);
        }
        return account;
    }

    private Transaction buildSavedTx(String code, TransactionStatus status, Account from, Account to, TransactionType type) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setTransactionCode(code);
        tx.setStatus(status);
        tx.setCurrency("CRC");
        tx.setAmount(new BigDecimal("100000"));
        tx.setFromAccount(from);
        tx.setToAccount(to);
        tx.setType(type);
        return tx;
    }

    private Transaction buildReversibleTransaction(Account from, Account to) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setTransactionCode("TXN-BCR-REV-001");
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setFromAccount(from);
        tx.setToAccount(to);
        tx.setAmount(new BigDecimal("100000"));
        tx.setCurrency("CRC");
        tx.setType(TransactionType.TRANSFER);
        // canBeReversed() requires createdAt within 90 days
        try {
            var field = Transaction.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(tx, LocalDateTime.now().minusDays(1));
        } catch (Exception e) {
            throw new IllegalStateException("Could not set createdAt", e);
        }
        return tx;
    }

    private TransactionRequestMetadataDto buildMetadata() {
        return new TransactionRequestMetadataDto(null, null, null);
    }

    private TransactionResponseDto buildResponseDto(String code) {
        return new TransactionResponseDto(
                code, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
