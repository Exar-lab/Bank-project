# Spec — transaction-email-notifications

## Functional Requirements

### Publishing (TransactionService)

**FR-001 — Notification OutboxEvent on TRANSFER COMPLETED**
When a TRANSFER reaches COMPLETED status, `TransactionService` MUST persist a second `OutboxEvent` targeting `KafkaTopic.TRANSACTION_NOTIFICATION_EVENTS` within the same `@Transactional` boundary that already persists the domain `OutboxEvent` to `TRANSACTION_EVENTS`. The notification payload MUST include both `fromAccount` and `toAccount` account details (accountCode, userId, userEmail, userFirstName) because both parties are non-null for a TRANSFER.

**FR-002 — Notification OutboxEvent on PAYMENT COMPLETED**
When a PAYMENT (card payment via `payment()` or service payment via `payService()`) reaches COMPLETED status, `TransactionService` MUST persist a notification `OutboxEvent` to `TRANSACTION_NOTIFICATION_EVENTS` in the same transaction. The payload MUST populate `fromAccount` with the payer's account details. `toAccount` MUST be null.

**FR-003 — Notification OutboxEvent on DEPOSIT COMPLETED**
When a DEPOSIT (cashDeposit or checkDeposit) reaches COMPLETED status, `TransactionService` MUST persist a notification `OutboxEvent` to `TRANSACTION_NOTIFICATION_EVENTS` in the same transaction. The payload MUST populate `toAccount` with the deposit recipient's account details. `fromAccount` MUST be null.

**FR-004 — Notification OutboxEvent on WITHDRAWAL COMPLETED**
When a WITHDRAWAL (cashWithdrawal) reaches COMPLETED status, `TransactionService` MUST persist a notification `OutboxEvent` to `TRANSACTION_NOTIFICATION_EVENTS` in the same transaction. The payload MUST populate `fromAccount` with the account holder's details. `toAccount` MUST be null.

**FR-005 — Notification payload contract**
Every notification `OutboxEvent` payload MUST conform to:
```json
{
  "eventType": "TransactionCompletedNotification",
  "transactionId": "<UUID>",
  "transactionCode": "TXN-BCR-2024-XXXXXXXX",
  "type": "TRANSFER|DEPOSIT|WITHDRAWAL|PAYMENT",
  "amount": 1234.56,
  "currency": "CRC",
  "occurredAt": "2026-04-22T14:35:12.345",
  "fromAccount": { "accountCode": "...", "userId": "...", "userEmail": "...", "userFirstName": "..." },
  "toAccount":   { "accountCode": "...", "userId": "...", "userEmail": "...", "userFirstName": "..." }
}
```
Fields `fromAccount` and `toAccount` are null (JSON `null`) when not applicable per transaction type.

**FR-006 — No notification event on FRAUD-BLOCKED transaction**
When `executeFraudGate` returns `BLOCKED` (throws `FraudBlockedException`), the transaction status becomes `FAILED`. No notification `OutboxEvent` MUST be persisted. The `@Transactional(noRollbackFor = FraudBlockedException.class)` boundary still commits, but only the domain `TransactionFraudBlocked` event to `TRANSACTION_EVENTS` is allowed.

**FR-007 — No notification event on FRAUD-SUSPICIOUS transaction**
When `executeFraudGate` returns `SUSPICIOUS`, the transaction is flagged for review (status not COMPLETED). No notification `OutboxEvent` MUST be persisted. Only the domain `TransactionFlaggedForReview` event to `TRANSACTION_EVENTS` is persisted.

**FR-008 — No notification event on REVERSED transaction**
`reverseTransaction()` publishes `TransactionReversed` to `TRANSACTION_EVENTS` only. No notification `OutboxEvent` to `TRANSACTION_NOTIFICATION_EVENTS` MUST be persisted.

**FR-009 — No notification event on SCHEDULED transfer**
`scheduleTransfer()` does not complete the transaction; it persists with status `SCHEDULED`. No notification `OutboxEvent` MUST be persisted.

**FR-010 — Overload unification**
The private overload `buildTransactionPayload(Transaction, Account, Account, BigDecimal)` (used today by cashDeposit, checkDeposit, cashWithdrawal) MUST be removed. All calls MUST be unified into the single overload `buildTransactionPayload(Transaction, String eventType)` (which already handles null from/to accounts). The notification payload builder is a NEW private method `buildNotificationPayload(Transaction)`.

**FR-011 — KafkaTopic enum extension**
`KafkaTopic` MUST include the new value:
```java
TRANSACTION_NOTIFICATION_EVENTS("banco.transaction.notification.events")
```

### Consumer (TransactionEventsEmailHandler)

**FR-012 — TRANSFER with different users → 2 EmailOutboxEvents**
When `TransactionEventsEmailHandler` receives a message with `type=TRANSFER` and `fromAccount.userId != toAccount.userId`, it MUST call `IEmailService.enqueue()` twice:
- Once for the sender with `eventId = "{transactionId}:sender"`, template `email/transaction-transfer-sender`, subject localized for sender
- Once for the receiver with `eventId = "{transactionId}:receiver"`, template `email/transaction-transfer-receiver`, subject localized for receiver

**FR-013 — TRANSFER same user (self-transfer) → 1 EmailOutboxEvent**
When `type=TRANSFER` and `fromAccount.userId == toAccount.userId`, the handler MUST enqueue only the sender email with `eventId = "{transactionId}:sender"`. No receiver email is enqueued.

**FR-014 — DEPOSIT → 1 EmailOutboxEvent to toAccount owner**
When `type=DEPOSIT`, the handler MUST enqueue one email to `toAccount.userEmail` with `eventId = "{transactionId}:operation"`, template `email/transaction-account-operation`.

**FR-015 — WITHDRAWAL → 1 EmailOutboxEvent to fromAccount owner**
When `type=WITHDRAWAL`, the handler MUST enqueue one email to `fromAccount.userEmail` with `eventId = "{transactionId}:operation"`, template `email/transaction-account-operation`.

**FR-016 — PAYMENT → 1 EmailOutboxEvent to fromAccount owner**
When `type=PAYMENT`, the handler MUST enqueue one email to `fromAccount.userEmail` with `eventId = "{transactionId}:operation"`, template `email/transaction-account-operation`.

**FR-017 — Unknown eventType → silent skip**
If the `eventType` field in the received message is not `TransactionCompletedNotification`, the handler MUST return immediately without enqueuing any email and without throwing.

**FR-018 — Malformed payload → log + no throw**
If the JSON payload cannot be deserialized (malformed JSON, missing required fields), the handler MUST log a WARN-level message and return without rethrowing. The consumer offset MUST be committed (no DLQ re-attempt for poison messages).

**FR-019 — Deduplication via eventId uniqueness**
`EmailOutboxEvent.eventId` has a UNIQUE constraint in the database. `IEmailService.enqueue()` already catches `DataIntegrityViolationException` and logs a dedup warning. The handler relies on this mechanism; no additional dedup logic is required in the handler itself.

### Template Context

**FR-020 — TransferSenderEmailContext record fields**
```java
public record TransferSenderEmailContext(
    String recipientName,
    BigDecimal amount,
    String currency,
    String counterpartyName,
    String counterpartyAccountCode,
    String fromAccountCode,
    String transactionCode,
    LocalDateTime occurredAt,
    String bankName
) {}
```

**FR-021 — TransferReceiverEmailContext record fields**
```java
public record TransferReceiverEmailContext(
    String recipientName,
    BigDecimal amount,
    String currency,
    String counterpartyName,
    String counterpartyAccountCode,
    String toAccountCode,
    String transactionCode,
    LocalDateTime occurredAt,
    String bankName
) {}
```

**FR-022 — AccountOperationEmailContext record fields**
```java
public record AccountOperationEmailContext(
    String recipientName,
    String operationType,
    String operationLabel,
    BigDecimal amount,
    String currency,
    String accountCode,
    String transactionCode,
    LocalDateTime occurredAt,
    String bankName
) {}
```
`operationType` = enum name (e.g. `"DEPOSIT"`). `operationLabel` = human-readable label (e.g. `"Depósito en cuenta"`).

---

## Non-Functional Requirements

**NFR-001 — Atomicity**
The notification `OutboxEvent` MUST be persisted in the exact same `@Transactional` boundary as the domain `OutboxEvent`. If the transaction rolls back, neither event is persisted. A crash between the two `outboxEventPort.save()` calls is acceptable; the domain event will still fire independently.

**NFR-002 — Idempotence**
`eventId` format for sender: `{transactionId}:sender`. For receiver: `{transactionId}:receiver`. For single-party operations: `{transactionId}:operation`. The UNIQUE constraint on `email_outbox_events.event_id` is the dedup guard. Re-consuming the same Kafka message produces at most one `EmailOutboxEvent` per `eventId`.

**NFR-003 — No PII in logs**
Email addresses MUST NOT appear in log messages at any level. User names MUST NOT appear in log messages. Log only `transactionId`, `transactionCode`, `eventId` (which contains only the UUID and a suffix). Use `{}` placeholders, never string concatenation.

**NFR-004 — No PII in event payload fields exposed to logs**
When logging errors or warnings related to payload parsing, log only the raw payload length or the exception message. Never log the full payload string (it may contain email addresses).

**NFR-005 — Consumer group**
`TransactionEventsEmailHandler` MUST use `groupId = "banco-service-group"` (same as the other handlers), `containerFactory = "kafkaListenerContainerFactory"`.

**NFR-006 — No outbox event for non-COMPLETED outcomes**
The notification topic carries only successful completions. Fraud-blocked, suspicious, reversed, rejected, and scheduled transactions MUST NOT produce notification events.

---

## Test Scenarios

### TransactionService — OutboxEvent emission

**TS-001 — TRANSFER emits 2 OutboxEvents: one domain, one notification**
```
Given: a valid TRANSFER request with distinct fromAccountCode and toAccountCode
       fraud gate returns CLEAR
When:  transfer() is called
Then:  outboxEventPort.save() is called exactly 2 times
       call #1 has kafkaTopic = TRANSACTION_EVENTS and eventType = "TransactionCompleted"
       call #2 has kafkaTopic = TRANSACTION_NOTIFICATION_EVENTS
                and payload contains "eventType":"TransactionCompletedNotification"
                and payload contains non-null fromAccount and toAccount with userEmail fields
```
Test class: `TransactionServiceTransferNotificationTest`
Test method: `testTransfer_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents`

**TS-002 — DEPOSIT emits 2 OutboxEvents: one domain, one notification**
```
Given: a valid cashDeposit request
When:  cashDeposit() is called
Then:  outboxEventPort.save() is called exactly 2 times
       call #1 has kafkaTopic = TRANSACTION_EVENTS
       call #2 has kafkaTopic = TRANSACTION_NOTIFICATION_EVENTS
                and payload contains fromAccount = null
                and payload contains toAccount with non-null userEmail
```
Test method: `testCashDeposit_Completed_EmitsDomainAndNotificationOutboxEvents`

**TS-003 — WITHDRAWAL emits 2 OutboxEvents: one domain, one notification**
```
Given: a valid cashWithdrawal request
       fraud gate returns CLEAR
When:  cashWithdrawal() is called
Then:  outboxEventPort.save() is called exactly 2 times
       call #2 kafkaTopic = TRANSACTION_NOTIFICATION_EVENTS
                payload contains toAccount = null
                payload contains fromAccount with non-null userEmail
```
Test method: `testCashWithdrawal_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents`

**TS-004 — PAYMENT emits 2 OutboxEvents: one domain, one notification**
```
Given: a valid payment() request (card payment)
       fraud gate returns CLEAR
When:  payment() is called
Then:  outboxEventPort.save() is called exactly 2 times
       call #2 kafkaTopic = TRANSACTION_NOTIFICATION_EVENTS
                payload contains toAccount = null
                payload contains fromAccount with non-null userEmail
```
Test method: `testPayment_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents`

**TS-005 — reverseTransaction does NOT emit notification OutboxEvent**
```
Given: a COMPLETED transaction that is eligible for reversal
When:  reverseTransaction() is called
Then:  outboxEventPort.save() is called exactly 1 time
       the single call has kafkaTopic = TRANSACTION_EVENTS and eventType = "TransactionReversed"
       TRANSACTION_NOTIFICATION_EVENTS topic is never passed to outboxEventPort.save()
```
Test method: `testReverseTransaction_DoesNotEmitNotificationEvent`

**TS-006 — fraud-BLOCKED transfer does NOT emit notification OutboxEvent**
```
Given: a TRANSFER request
       fraud gate returns BLOCKED (throws FraudBlockedException)
When:  transfer() is called (expect FraudBlockedException to propagate)
Then:  outboxEventPort.save() is called exactly 1 time
       the single call has kafkaTopic = TRANSACTION_EVENTS and eventType = "TransactionFraudBlocked"
       TRANSACTION_NOTIFICATION_EVENTS is never used
```
Test method: `testTransfer_FraudBlocked_DoesNotEmitNotificationEvent`

---

### TransactionEventsEmailHandler — consumer routing

**TS-007 — TRANSFER different users → 2 EmailOutboxEvents enqueued**
```
Given: a valid TransactionCompletedNotification payload with type=TRANSFER
       fromAccount.userId = "uuid-alice", toAccount.userId = "uuid-bob" (different)
When:  consume(payload) is called
Then:  emailService.enqueue() is called exactly 2 times
       call with eventId = "{transactionId}:sender"
           templateName = "email/transaction-transfer-sender"
           recipientEmail = fromAccount.userEmail
       call with eventId = "{transactionId}:receiver"
           templateName = "email/transaction-transfer-receiver"
           recipientEmail = toAccount.userEmail
```
Test class: `TransactionEventsEmailHandlerTest`
Test method: `testConsume_TransferDifferentUsers_EnqueuesTwoEmails`

**TS-008 — TRANSFER same user (self-transfer) → 1 EmailOutboxEvent enqueued**
```
Given: a valid payload with type=TRANSFER
       fromAccount.userId == toAccount.userId
When:  consume(payload) is called
Then:  emailService.enqueue() is called exactly 1 time
       the single call has eventId = "{transactionId}:sender"
       templateName = "email/transaction-transfer-sender"
```
Test method: `testConsume_TransferSameUser_EnqueuesOnlySenderEmail`

**TS-009 — DEPOSIT → 1 email to toAccount owner**
```
Given: a valid payload with type=DEPOSIT
       fromAccount = null
       toAccount.userEmail = "bob@example.com"
When:  consume(payload) is called
Then:  emailService.enqueue() is called exactly 1 time
       eventId = "{transactionId}:operation"
       templateName = "email/transaction-account-operation"
       recipientEmail = "bob@example.com"
       templateContext contains operationType = "DEPOSIT"
```
Test method: `testConsume_Deposit_EnqueuesEmailToToAccountOwner`

**TS-010 — WITHDRAWAL → 1 email to fromAccount owner**
```
Given: a valid payload with type=WITHDRAWAL
       fromAccount.userEmail = "alice@example.com"
       toAccount = null
When:  consume(payload) is called
Then:  emailService.enqueue() is called exactly 1 time
       eventId = "{transactionId}:operation"
       templateName = "email/transaction-account-operation"
       recipientEmail = "alice@example.com"
       templateContext contains operationType = "WITHDRAWAL"
```
Test method: `testConsume_Withdrawal_EnqueuesEmailToFromAccountOwner`

**TS-011 — PAYMENT → 1 email to fromAccount owner**
```
Given: a valid payload with type=PAYMENT
       fromAccount.userEmail = "alice@example.com"
       toAccount = null
When:  consume(payload) is called
Then:  emailService.enqueue() is called exactly 1 time
       eventId = "{transactionId}:operation"
       templateName = "email/transaction-account-operation"
       recipientEmail = "alice@example.com"
       templateContext contains operationType = "PAYMENT"
```
Test method: `testConsume_Payment_EnqueuesEmailToFromAccountOwner`

**TS-012 — Unknown eventType → no enqueue, no exception**
```
Given: a payload with eventType = "TransactionApproved" (not TransactionCompletedNotification)
When:  consume(payload) is called
Then:  emailService.enqueue() is never called
       no exception is thrown
```
Test method: `testConsume_UnknownEventType_IgnoresMessageSilently`

**TS-013 — Malformed payload → log warn, no exception**
```
Given: consume is called with payload = "{not-valid-json"
When:  consume(payload) is called
Then:  no exception propagates out of consume()
       emailService.enqueue() is never called
       (verify via Mockito verifyNoInteractions)
```
Test method: `testConsume_MalformedPayload_LogsAndReturnsWithoutThrowing`

---

### Idempotency

**TS-014 — Same eventId enqueued twice → 1 row in email_outbox_events**
```
Given: a TransactionEventsEmailHandler backed by a real EmailServiceImpl
       and a real EmailOutboxEventJpaRepository (Testcontainers / H2)
       and a TRANSFER payload with transactionId = "txn-123"
When:  consume(payload) is called twice with the identical payload
Then:  email_outbox_events table contains exactly 1 row with event_id = "txn-123:sender"
       and 1 row with event_id = "txn-123:receiver"
       (second consume produces DataIntegrityViolationException internally, caught by EmailServiceImpl)
       no exception propagates to the test
```
Test class: `TransactionEventsEmailHandlerIdempotencyTest` (integration, Testcontainers or H2)
Test method: `testIdempotency_SamePayloadTwice_ProducesOneRowPerEventId`

---

### DTO Records

**TS-015 — TransferSenderEmailContext — all fields mapped correctly**
```
Given: accountCode = "AC-BCR-0001", counterpartyName = "Bob", amount = 5000.00,
       currency = "CRC", transactionCode = "TXN-BCR-2024-X7K9P2M3"
When:  TransferSenderEmailContext record is constructed
Then:  record.fromAccountCode() == "AC-BCR-0001"
       record.counterpartyName() == "Bob"
       record.amount() == 5000.00
       record.currency() == "CRC"
       record.transactionCode() == "TXN-BCR-2024-X7K9P2M3"
```
Test class: `TransferSenderEmailContextTest`
Test method: `testRecord_AllFieldsAccessible`

**TS-016 — AccountOperationEmailContext — DEPOSIT sets operationLabel**
```
Given: operationType = "DEPOSIT"
When:  handler builds AccountOperationEmailContext for a DEPOSIT
Then:  templateContext map passed to emailService.enqueue() contains
       key "operationType" = "DEPOSIT"
       key "operationLabel" = non-blank human-readable string (e.g. "Depósito en cuenta")
```
Test method: `testConsume_Deposit_OperationLabelIsSet` (can be verified in TS-009 extended assertion)
