package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.service.IEmailService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for TransactionEventsEmailHandler.
 * TS-007 through TS-013.
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventsEmailHandlerTest {

    @Mock  private IEmailService emailService;
    @Spy   private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private TransactionEventsEmailHandler handler;

    private static final String TX_ID        = UUID.randomUUID().toString();
    private static final String FROM_USER_ID = UUID.randomUUID().toString();
    private static final String TO_USER_ID   = UUID.randomUUID().toString();

    // ══════════════════════════════════════════════════════════
    //  TS-007 — TRANSFER different users → enqueue x2
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_TransferDifferentUsers_EnqueuesTwoEmails() throws Exception {
        String payload = buildTransferPayload(TX_ID, FROM_USER_ID, TO_USER_ID, "sender@banco.co", "receiver@banco.co", "Ana", "Pedro");

        handler.consume(payload);

        ArgumentCaptor<String> eventIdCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> templateCaptor  = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).enqueue(
                eventIdCaptor.capture(), any(), any(), any(), templateCaptor.capture(), anyMap(), any());

        assertThat(eventIdCaptor.getAllValues())
                .containsExactlyInAnyOrder(TX_ID + ":sender", TX_ID + ":receiver");
        assertThat(templateCaptor.getAllValues())
                .containsExactlyInAnyOrder("email/transaction-transfer-sender", "email/transaction-transfer-receiver");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-008 — TRANSFER same user → enqueue x1 sender only
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_TransferSameUser_EnqueuesOnlySenderEmail() throws Exception {
        String payload = buildTransferPayload(TX_ID, FROM_USER_ID, FROM_USER_ID, "same@banco.co", "same@banco.co", "Ana", "Ana");

        handler.consume(payload);

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).enqueue(
                eventIdCaptor.capture(), any(), any(), any(),
                eq("email/transaction-transfer-sender"), anyMap(), any());
        assertThat(eventIdCaptor.getValue()).isEqualTo(TX_ID + ":sender");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-009 — DEPOSIT → enqueue x1 to toAccount owner
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_Deposit_EnqueuesEmailToToAccountOwner() throws Exception {
        String payload = buildOperationPayload(TX_ID, "DEPOSIT", null, null, TO_USER_ID, "ACC-TO-001", "receiver@banco.co", "Pedro");

        handler.consume(payload);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enqueue(
                eq(TX_ID + ":operation"),
                eq(UUID.fromString(TO_USER_ID)),
                emailCaptor.capture(),
                eq("Pedro"),
                eq("email/transaction-account-operation"),
                anyMap(),
                any());
        assertThat(emailCaptor.getValue()).isEqualTo("receiver@banco.co");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-010 — WITHDRAWAL → enqueue x1 to fromAccount owner
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_Withdrawal_EnqueuesEmailToFromAccountOwner() throws Exception {
        String payload = buildOperationPayload(TX_ID, "WITHDRAWAL", FROM_USER_ID, "ACC-FROM-001", null, null, "sender@banco.co", "Ana");

        handler.consume(payload);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enqueue(
                eq(TX_ID + ":operation"),
                eq(UUID.fromString(FROM_USER_ID)),
                emailCaptor.capture(),
                eq("Ana"),
                eq("email/transaction-account-operation"),
                anyMap(),
                any());
        assertThat(emailCaptor.getValue()).isEqualTo("sender@banco.co");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-011 — PAYMENT → enqueue x1 to fromAccount owner
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_Payment_EnqueuesEmailToFromAccountOwner() throws Exception {
        String payload = buildOperationPayload(TX_ID, "PAYMENT", FROM_USER_ID, "ACC-FROM-001", null, null, "sender@banco.co", "Ana");

        handler.consume(payload);

        verify(emailService).enqueue(
                eq(TX_ID + ":operation"),
                eq(UUID.fromString(FROM_USER_ID)),
                eq("sender@banco.co"),
                eq("Ana"),
                eq("email/transaction-account-operation"),
                anyMap(),
                any());
    }

    // ══════════════════════════════════════════════════════════
    //  TS-012 — unknown eventType → silent skip
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_UnknownEventType_IgnoresMessageSilently() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("eventType", "TransactionApproved");
        map.put("transactionId", TX_ID);

        handler.consume(objectMapper.writeValueAsString(map));

        verifyNoInteractions(emailService);
    }

    // ══════════════════════════════════════════════════════════
    //  TS-013 — malformed payload → WARN, no throw, no enqueue
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_MalformedPayload_LogsAndReturnsWithoutThrowing() {
        assertThatCode(() -> handler.consume("not-valid-{json")).doesNotThrowAnyException();
        verifyNoInteractions(emailService);
    }

    // ══════════════════════════════════════════════════════════
    //  Payload builders
    // ══════════════════════════════════════════════════════════

    private String buildTransferPayload(String txId, String fromUserId, String toUserId,
                                        String fromEmail, String toEmail,
                                        String fromName, String toName) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("eventType", "TransactionCompletedNotification");
        map.put("transactionId", txId);
        map.put("transactionCode", "TXN-BCR-001");
        map.put("type", "TRANSFER");
        map.put("amount", new BigDecimal("100000"));
        map.put("currency", "CRC");
        map.put("occurredAt", LocalDateTime.now().toString());

        Map<String, Object> from = new HashMap<>();
        from.put("accountCode", "ACC-FROM-001");
        from.put("userId", fromUserId);
        from.put("userEmail", fromEmail);
        from.put("userFirstName", fromName);

        Map<String, Object> to = new HashMap<>();
        to.put("accountCode", "ACC-TO-001");
        to.put("userId", toUserId);
        to.put("userEmail", toEmail);
        to.put("userFirstName", toName);

        map.put("fromAccount", from);
        map.put("toAccount", to);
        return objectMapper.writeValueAsString(map);
    }

    private String buildOperationPayload(String txId, String type,
                                         String fromUserId, String fromAccountCode,
                                         String toUserId,   String toAccountCode,
                                         String email, String name) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("eventType", "TransactionCompletedNotification");
        map.put("transactionId", txId);
        map.put("transactionCode", "TXN-BCR-001");
        map.put("type", type);
        map.put("amount", new BigDecimal("50000"));
        map.put("currency", "CRC");
        map.put("occurredAt", LocalDateTime.now().toString());

        if (fromUserId != null) {
            Map<String, Object> from = new HashMap<>();
            from.put("accountCode", fromAccountCode);
            from.put("userId", fromUserId);
            from.put("userEmail", email);
            from.put("userFirstName", name);
            map.put("fromAccount", from);
            map.put("toAccount", null);
        } else {
            Map<String, Object> to = new HashMap<>();
            to.put("accountCode", toAccountCode);
            to.put("userId", toUserId);
            to.put("userEmail", email);
            to.put("userFirstName", name);
            map.put("toAccount", to);
            map.put("fromAccount", null);
        }
        return objectMapper.writeValueAsString(map);
    }
}
