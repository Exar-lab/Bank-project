# Design — transaction-email-notifications

## Goal
Emit a second Kafka event on every COMPLETED transaction to a dedicated notification topic, consume it asynchronously in a new handler, enrich it with user contact data already embedded in the payload, and enqueue one or two localized emails via the existing `IEmailService`.

The Outbox Pattern stays authoritative. The new wiring layers onto the existing outbox without touching `OutboxEvent`, `IOutboxEventPort`, `KafkaEventPublisher`, or `OutboxScheduler`.

---

## Class Signatures

### 1. `com.banco.co.outbox.enums.KafkaTopic` (MODIFIED)

Add one value at the end:

```java
public enum KafkaTopic {
    ACCOUNT_EVENTS("banco.account.events"),
    TRANSACTION_EVENTS("banco.transaction.events"),
    ENVELOPE_EVENTS("banco.envelope.events"),
    USER_EVENTS("banco.user.events"),
    CARD_EVENTS("banco.card.events"),
    TRANSACTION_NOTIFICATION_EVENTS("banco.transaction.notification.events");
    // existing constructor, getter unchanged
}
```

No other changes to the enum body. Adding a value at the end preserves ordinal positions for values already persisted in `outbox_events.kafka_topic` (though `@Enumerated(EnumType.STRING)` means ordinals are irrelevant — name match is what persists).

---

### 2. `TransferSenderEmailContext` record (NEW)

File: `banco-service/src/main/java/com/banco/co/notification/email/dto/TransferSenderEmailContext.java`

```java
package com.banco.co.notification.email.dto;

public record TransferSenderEmailContext(
        String recipientName,
        String amount,
        String currency,
        String counterpartyName,
        String counterpartyAccountCode,
        String fromAccountCode,
        String transactionCode,
        String occurredAt,
        String bankName
) {
}
```

### 3. `TransferReceiverEmailContext` record (NEW)

File: `banco-service/src/main/java/com/banco/co/notification/email/dto/TransferReceiverEmailContext.java`

```java
package com.banco.co.notification.email.dto;

public record TransferReceiverEmailContext(
        String recipientName,
        String amount,
        String currency,
        String counterpartyName,
        String counterpartyAccountCode,
        String toAccountCode,
        String transactionCode,
        String occurredAt,
        String bankName
) {
}
```

### 4. `AccountOperationEmailContext` record (NEW)

File: `banco-service/src/main/java/com/banco/co/notification/email/dto/AccountOperationEmailContext.java`

```java
package com.banco.co.notification.email.dto;

public record AccountOperationEmailContext(
        String recipientName,
        String operationType,   // "DEPOSIT", "WITHDRAWAL", "PAYMENT" (enum name)
        String operationLabel,  // "Depósito", "Retiro", "Pago" (es-CR label for subject/body)
        String amount,
        String currency,
        String accountCode,
        String transactionCode,
        String occurredAt,
        String bankName
) {
}
```

---

### 5. `TransactionEventsEmailHandler` (NEW)

File: `banco-service/src/main/java/com/banco/co/notification/email/adapter/in/TransactionEventsEmailHandler.java`

```java
package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.dto.AccountOperationEmailContext;
import com.banco.co.notification.email.dto.TransferReceiverEmailContext;
import com.banco.co.notification.email.dto.TransferSenderEmailContext;
import com.banco.co.notification.email.service.IEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class TransactionEventsEmailHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventsEmailHandler.class);
    private static final String TX_COMPLETED_NOTIFICATION = "TransactionCompletedNotification";
    private static final String BANK_NAME = "Banco CO";

    private final ObjectMapper objectMapper;
    private final IEmailService emailService;

    public TransactionEventsEmailHandler(ObjectMapper objectMapper, IEmailService emailService);

    @KafkaListener(
            topics = "banco.transaction.notification.events",
            groupId = "banco-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(@Payload String payload);

    // ── dispatch by type ────────────────────────────────────────────
    private void handleTransfer(JsonNode node, UUID transactionId, String transactionCode,
                                String amount, String currency, String occurredAt);

    private void handleDeposit(JsonNode node, UUID transactionId, String transactionCode,
                               String amount, String currency, String occurredAt);

    private void handleWithdrawalOrPayment(JsonNode node, UUID transactionId, String transactionCode,
                                           String amount, String currency, String occurredAt,
                                           String operationTypeEnum, String operationLabel);

    // ── enqueue helpers ─────────────────────────────────────────────
    private void enqueueSenderEmail(UUID transactionId, JsonNode fromAccount, JsonNode toAccount,
                                    String amount, String currency, String transactionCode, String occurredAt);

    private void enqueueReceiverEmail(UUID transactionId, JsonNode fromAccount, JsonNode toAccount,
                                      String amount, String currency, String transactionCode, String occurredAt);

    private void enqueueOperationEmail(UUID transactionId, JsonNode account,
                                       String operationTypeEnum, String operationLabel,
                                       String amount, String currency, String transactionCode, String occurredAt);

    // ── safe accessors ──────────────────────────────────────────────
    private String textOrNull(JsonNode node, String field);

    private UUID uuidOrNull(String raw);

    private String formatAmount(JsonNode amountNode);

    private String subjectForOperation(String operationLabel);
}
```

Notes:
- Single constructor — two deps, constructor-injected, `private final`.
- `@Transactional` on `consume` mirrors the existing `EnvelopeEventsEmailHandler` pattern so the `EmailOutboxEvent` insert participates in the consumer transaction.
- The handler reads user email/firstName **directly from the payload** (they travel in `fromAccount`/`toAccount` nested objects). It does NOT call `IUserRepository`, avoiding an extra DB round-trip and honoring the proposal's payload contract.

---

### 6. `TransactionService` — MODIFIED

Changes (surgical — only what's in scope):

#### 6.1 New private method

```java
private String buildTransactionNotificationPayload(Transaction transaction);
```

Replaces the **need** for the 4-arg overload in the three call sites that still use it (`cashDeposit`, `cashWithdrawal`, `checkDeposit`). Those three call sites will be migrated to use the 2-arg `buildTransactionPayload(Transaction, String)` for the `TRANSACTION_EVENTS` topic (keeping the same audit-grade payload shape) and the new `buildTransactionNotificationPayload(Transaction)` for the `TRANSACTION_NOTIFICATION_EVENTS` topic.

#### 6.2 Deleted overload

```java
// DELETED
private String buildTransactionPayload(Transaction transaction, Account fromAccount, Account toAccount, BigDecimal amount);
```

Rationale: the 2-arg `buildTransactionPayload(Transaction, String)` already reads `fromAccount` and `toAccount` off the `Transaction`. The 4-arg version exists only because `cashDeposit`/`cashWithdrawal`/`checkDeposit` predate it. Unifying on the 2-arg form removes duplication and keeps the audit payload consistent across every operation.

#### 6.3 Affected public methods — what changes

Each method below must emit BOTH outbox events on the happy COMPLETED path, inside the existing `@Transactional`, right where the single `outboxEventPort.save(...)` call exists today:

| Method | Current outbox call | New behavior |
|---|---|---|
| `transfer(...)` | 1x save to `TRANSACTION_EVENTS` (`TransactionCompleted`) | + 1x save to `TRANSACTION_NOTIFICATION_EVENTS` (`TransactionCompletedNotification`) — same tx |
| `payment(...)` | 1x save to `TRANSACTION_EVENTS` (`TransactionCompleted`) | + 1x save to `TRANSACTION_NOTIFICATION_EVENTS` — same tx |
| `payService(...)` | 1x save to `TRANSACTION_EVENTS` (`TransactionCompleted`) | + 1x save to `TRANSACTION_NOTIFICATION_EVENTS` — same tx |
| `cashDeposit(...)` | 1x save to `TRANSACTION_EVENTS` using 4-arg payload | 2-arg payload for audit topic + 1x notification topic save |
| `cashWithdrawal(...)` | 1x save to `TRANSACTION_EVENTS` using 4-arg payload | 2-arg payload + 1x notification topic save |
| `checkDeposit(...)` | 1x save to `TRANSACTION_EVENTS` using 4-arg payload | 2-arg payload + 1x notification topic save |
| `approveTransaction(...)` (when transitions to COMPLETED via `completeFromApproved`) | 1x save, eventType switches between `TransactionApproved` and `TransactionCompleted` | + 1x save to `TRANSACTION_NOTIFICATION_EVENTS` **only when status ends up COMPLETED** (i.e. when `eventType == "TransactionCompleted"`) |

Not modified (explicitly): `executeFraudGate` (SUSPICIOUS / BLOCKED do not emit notification events — no money moved), `reverseTransaction` (REVERSED is a different notification story, out of scope), `scheduleTransfer` (SCHEDULED is not COMPLETED).

No new fields on `TransactionService`. The existing `ObjectMapper` bean handles serialization.

---

## `buildTransactionNotificationPayload` — Detail

Signature:

```java
private String buildTransactionNotificationPayload(Transaction transaction);
```

Logic (pseudocode):

```
root = new LinkedHashMap<String, Object>()
root.put("eventType", "TransactionCompletedNotification")
root.put("transactionId", transaction.getId().toString())
root.put("transactionCode", transaction.getTransactionCode())
root.put("type", transaction.getType().name())                // TRANSFER | DEPOSIT | WITHDRAWAL | PAYMENT
root.put("amount", transaction.getAmount())                   // BigDecimal — Jackson serializes plain (see config)
root.put("currency", transaction.getCurrency() != null ? transaction.getCurrency() : "CRC")
root.put("occurredAt", resolveOccurredAt(transaction))        // see below

root.put("fromAccount", buildAccountNode(transaction.getFromAccount()))   // null for DEPOSIT
root.put("toAccount",   buildAccountNode(transaction.getToAccount()))     // null for WITHDRAWAL / PAYMENT

try {
    return objectMapper.writeValueAsString(root)
} catch (JsonProcessingException e) {
    throw new IllegalStateException("Failed to serialize notification payload", e)
}
```

### `resolveOccurredAt(Transaction)`

Precedence (first non-null wins):
1. `transaction.getCompletedAt()` — set by `Transaction.complete()` / `Transaction.completeFromApproved()`. This is the canonical "when it actually completed" timestamp for notifications.
2. `transaction.getProcessedAt()` — fallback if somehow completedAt is null (defensive — should not happen on the COMPLETED paths we care about).
3. `LocalDateTime.now()` — last-resort fallback.

Serialized as ISO-8601 without offset: `transaction.getCompletedAt().toString()` → `"2026-04-22T14:35:12.345"`. This matches the payload contract sample and is idempotent for Jackson's default `LocalDateTime` serializer (with `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false`, which is already implied by the existing envelope/user payloads rendering as strings).

### `buildAccountNode(Account account)`

```
if (account == null) return null
User u = account.getUser()   // LAZY — fine, we're still inside @Transactional
node = new LinkedHashMap<String, Object>()
node.put("accountCode", account.getAccountCode())
node.put("userId", u.getId().toString())
node.put("userEmail", u.getEmail())
node.put("userFirstName", u.getFistName())    // note: existing typo in User entity — do not fix in this change
return node
```

Rules the payload contract enforces:
- `fromAccount == null` for `DEPOSIT` (no source).
- `toAccount == null` for `WITHDRAWAL` / `PAYMENT` (no destination held at our bank).
- For `TRANSFER`, both are present. If `fromAccount.userId == toAccount.userId` (same-user transfer between own accounts), the handler still fills both — the consumer decides whether to send one or two emails (proposal consumer logic).

### Serialization

Uses the `ObjectMapper` already injected into `TransactionService` — same one producing the existing `TRANSACTION_EVENTS` payloads. No custom config needed. `BigDecimal` serializes as a JSON number with plain notation (Jackson default is `BigDecimal`-as-number; if the running config enables `WRITE_BIGDECIMAL_AS_PLAIN` globally that's fine, if not the number form is still accepted by the consumer which reads it as text via `amountNode.asText()` or `new BigDecimal(amountNode.asText())`).

---

## Sequence: `TransactionService.transfer()` — annotated

Focus on what is ADDED. Every step that existed before stays.

```
1.  userService.getEntityUserByEmail(userEmail)                              → User
2.  accountService.findAccountWithUserByAccountCode(dto.fromAccountCode())   → fromAccount (with user fetched)
3.  accountService.findAccountWithUserByAccountCode(dto.toAccountCode())     → toAccount   (with user fetched)
4.  Ownership + same-account guards (unchanged)
5.  accountService.validateCanWithdraw(fromAccount, amount)
6.  accountService.validateCanReceiveDeposit(toAccount)
7.  Build Transaction entity, PENDING, TRANSFER, metadata enrichment
8.  transaction.process()             → PROCESSING + processedAt
9.  fromAccount.blockFunds(amount)
10. transactionRepository.save(transaction) → savedTransaction (UUID + transactionCode generated)
11. executeFraudGate(savedTransaction, fromAccount, amount)
    • CLEAR  → continue to 12
    • SUSPICIOUS → updateBalance(from), return DTO (no notification event — not COMPLETED)
    • BLOCKED → throw FraudBlockedException (no notification event — not COMPLETED)
12. savedTransaction.complete()       → COMPLETED + completedAt   ← this is our occurredAt anchor
13. fromAccount.confirmBlockedFunds(amount)
14. toAccount.deposit(amount)
15. savedTransaction.setToAccountBalanceAfter(...)
16. transactionRepository.save(savedTransaction)
17. accountService.updateBalance(fromAccount) + updateBalance(toAccount)
18. auditLogService.logSuccess(...)
19. outboxEventPort.save(new OutboxEvent(                                 ← EXISTING (audit topic)
        "Transaction", savedTx.id, "TransactionCompleted",
        buildTransactionPayload(savedTx, "TransactionCompleted"),
        KafkaTopic.TRANSACTION_EVENTS))

20. log.info("Publishing transaction notification event — transactionId={}", savedTx.getId())   ← NEW
21. outboxEventPort.save(new OutboxEvent(                                 ← NEW (notification topic)
        "Transaction", savedTx.id, "TransactionCompletedNotification",
        buildTransactionNotificationPayload(savedTx),
        KafkaTopic.TRANSACTION_NOTIFICATION_EVENTS))

22. log.info("Transfer completed: …")  (existing)
23. return transactionMapper.toDto(savedTransaction)
```

Key guarantees:
- Both `OutboxEvent` rows are written in the SAME JPA `@Transactional` context. If anything after step 12 fails, both are rolled back atomically — no orphan notification.
- Outbox relay / Kafka publish is completely independent: happens asynchronously from `OutboxScheduler`. No change there.
- `SUSPICIOUS` and `BLOCKED` paths do NOT emit notification events because the transaction is not COMPLETED (requirement from proposal: "en cada COMPLETED emitir segundo OutboxEvent").

The same insertion pattern applies to `payment`, `payService`, `cashDeposit`, `cashWithdrawal`, `checkDeposit`, and the COMPLETED branch of `approveTransaction` (specifically when `transaction.isFlaggedForFraud()` is true AND `completeFromApproved()` ran; the event type emitted to the audit topic is `TransactionCompleted`, and the notification save mirrors that condition).

---

## Sequence: `TransactionEventsEmailHandler.consume()`

Input: raw JSON payload String, pushed by Kafka listener container (Spring Kafka deserializer produces String; no binding to DTO).

```
 1. try { node = objectMapper.readTree(payload) }
    catch (JsonProcessingException) { log.error(...); return }      // poison-pill swallow — parsing errors don't retry
 2. eventType = node.path("eventType").asText()
 3. if (!"TransactionCompletedNotification".equals(eventType)) { return }    // tolerate future event types
 4. transactionIdRaw = node.path("transactionId").asText()
    transactionId    = uuidOrNull(transactionIdRaw)                 // null → log.warn + return
 5. transactionCode  = textOrNull(node, "transactionCode")
    type             = node.path("type").asText()                   // TRANSFER | DEPOSIT | WITHDRAWAL | PAYMENT
    amount           = formatAmount(node.path("amount"))            // see Amount Formatting
    currency         = node.path("currency").asText("CRC")
    occurredAt       = node.path("occurredAt").asText()             // already ISO-8601 string from producer
 6. switch on type:
        "TRANSFER":
            handleTransfer(node, transactionId, transactionCode, amount, currency, occurredAt)
        "DEPOSIT":
            handleDeposit(node, transactionId, transactionCode, amount, currency, occurredAt)
        "WITHDRAWAL":
            handleWithdrawalOrPayment(node, transactionId, transactionCode, amount, currency,
                                      occurredAt, "WITHDRAWAL", "Retiro")
        "PAYMENT":
            handleWithdrawalOrPayment(node, transactionId, transactionCode, amount, currency,
                                      occurredAt, "PAYMENT", "Pago")
        default:
            log.warn("TransactionEventsEmailHandler ignored unknown type"); return
```

### `handleTransfer` flow

```
1. from = node.path("fromAccount")
   to   = node.path("toAccount")
2. fromUserIdRaw = from.path("userId").asText()
   toUserIdRaw   = to.path("userId").asText()
3. sameUser = fromUserIdRaw.equals(toUserIdRaw) && !fromUserIdRaw.isEmpty()
4. enqueueSenderEmail(transactionId, from, to, amount, currency, transactionCode, occurredAt)
5. if (!sameUser) {
       enqueueReceiverEmail(transactionId, from, to, amount, currency, transactionCode, occurredAt)
   }
```

### `enqueueSenderEmail` flow

```
1. recipientEmail = from.path("userEmail").asText()
2. recipientName  = from.path("userFirstName").asText()
3. counterpartyName = to.path("userFirstName").asText()
4. ctx = new TransferSenderEmailContext(
       recipientName, amount, currency,
       counterpartyName, to.path("accountCode").asText(),
       from.path("accountCode").asText(),
       transactionCode, occurredAt, "Banco CO")
5. Map<String,Object> templateContext = Map.of(
       "recipientName", ctx.recipientName(),
       "amount", ctx.amount(), "currency", ctx.currency(),
       "counterpartyName", ctx.counterpartyName(),
       "counterpartyAccountCode", ctx.counterpartyAccountCode(),
       "fromAccountCode", ctx.fromAccountCode(),
       "transactionCode", ctx.transactionCode(),
       "occurredAt", ctx.occurredAt(),
       "bankName", ctx.bankName())
6. eventId = transactionId + ":sender"
7. userId  = uuidOrNull(from.path("userId").asText())  // guard null → log.warn + return
8. log.info("Enqueueing sender email for transactionId={}", transactionId)
9. emailService.enqueue(
       eventId, userId, recipientEmail, recipientName,
       "email/transaction-transfer-sender",
       templateContext,
       "Transferencia enviada")
```

### `enqueueReceiverEmail` flow

Symmetric: swap source/destination, template `email/transaction-transfer-receiver`, eventId `transactionId + ":receiver"`, subject `"Transferencia recibida"`, `toAccountCode` instead of `fromAccountCode`.

### `handleDeposit`

```
1. to = node.path("toAccount")
   if to is missing/null → log.warn + return
2. enqueueOperationEmail(transactionId, to, "DEPOSIT", "Depósito",
                         amount, currency, transactionCode, occurredAt)
```

### `handleWithdrawalOrPayment`

```
1. from = node.path("fromAccount")
   if from is missing/null → log.warn + return
2. enqueueOperationEmail(transactionId, from, operationTypeEnum, operationLabel,
                         amount, currency, transactionCode, occurredAt)
```

### `enqueueOperationEmail`

```
1. recipientEmail = account.path("userEmail").asText()
2. recipientName  = account.path("userFirstName").asText()
3. ctx = new AccountOperationEmailContext(
       recipientName, operationTypeEnum, operationLabel,
       amount, currency, account.path("accountCode").asText(),
       transactionCode, occurredAt, "Banco CO")
4. templateContext = Map.of( …each ctx field by name… )
5. eventId = transactionId + ":operation"
6. userId = uuidOrNull(account.path("userId").asText())
7. subject = subjectForOperation(operationLabel)
   // DEPOSIT → "Depósito acreditado"
   // WITHDRAWAL → "Retiro procesado"
   // PAYMENT → "Pago realizado"
8. log.info("Enqueueing operation email type={} transactionId={}", operationTypeEnum, transactionId)
9. emailService.enqueue(eventId, userId, recipientEmail, recipientName,
                        "email/transaction-account-operation",
                        templateContext, subject)
```

### Dedup at the email outbox layer

`IEmailService.enqueue(...)` catches `DataIntegrityViolationException` on duplicate `eventId` (unique constraint already present on `EmailOutboxEvent.eventId`). This means at-least-once Kafka delivery is safe: a redelivery of the same notification event results in two `enqueue` calls that are de-duplicated at INSERT. No extra work in the handler.

---

## Amount Formatting

Decision: **`BigDecimal.toPlainString()` inside `formatAmount(JsonNode)` in the handler.**

Rationale:
- The producer writes `amount` as a JSON number (Jackson default for `BigDecimal`). On the consumer side, `node.path("amount").asText()` yields the plain textual form ("1234.56"), but for very small or very large numbers Jackson may emit scientific notation. Re-parsing through `new BigDecimal(amountNode.asText()).toPlainString()` guarantees human-readable output in every template variable.
- `NumberFormat`/`java.text.DecimalFormat` adds locale formatting (grouping separators) that **will** surprise test assertions and doesn't match the existing templates — `envelope-goal-reached.html` renders `goalAmount` as a plain string and so should we, consistently.
- `String.format("%.2f", ...)` forces 2 decimals but hides higher-precision currency (none in banco-service today, but forward-compatible — better to keep scale intact).

Implementation:

```java
private String formatAmount(JsonNode amountNode) {
    if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
        return "0";
    }
    return new BigDecimal(amountNode.asText()).toPlainString();
}
```

Applied consistently to `TransferSenderEmailContext.amount()`, `TransferReceiverEmailContext.amount()`, and `AccountOperationEmailContext.amount()`.

---

## Outbox Components — Unchanged

The following components are **not** modified in this change. Their contracts are preserved end-to-end.

| Component | Why it doesn't change |
|---|---|
| `com.banco.co.outbox.model.OutboxEvent` | Already supports arbitrary (`String aggregateType, String aggregateId, String eventType, String payload, KafkaTopic kafkaTopic`) — one new `KafkaTopic` value is the only thing it needs. |
| `com.banco.co.outbox.enums.OutboxStatus` | Lifecycle (PENDING → PUBLISHED/FAILED) is topic-agnostic. |
| `com.banco.co.outbox.port.IOutboxEventPort` | `save(OutboxEvent)` is reused as-is — just called twice in the same transaction now. |
| `com.banco.co.outbox.repository.IOutboxEventRepository` | Same interface; queries are topic-agnostic (filter by status + created_at only). |
| `com.banco.co.outbox.adapter.KafkaEventPublisher` | Reads `event.getKafkaTopic().getTopicName()` and publishes — new topic name auto-routes. |
| `com.banco.co.outbox.config.KafkaProducerConfig` | Single `KafkaTemplate<String,String>` bean — serializer already string-based; no per-topic config. |
| `com.banco.co.outbox.config.KafkaConsumerConfig` | Default `kafkaListenerContainerFactory` factory is shared across all `@KafkaListener`s. New handler reuses it. |
| `com.banco.co.outbox.service.OutboxScheduler` | Polls `status = PENDING` ordered by `created_at` — topic-agnostic. Picks up both events automatically. |
| `ITransactionMapper`, `ITransactionMetadataEnricher`, `IFraudDetectionService` | Zero touch — orchestration before outbox save is unchanged. |
| `IEmailService` / `EmailServiceImpl` / `EmailOutboxEvent` / `EmailOutboxRelay` / `EmailOutboxDispatchWorker` / `JavaMailEmailDispatcher` / `ThymeleafEmailTemplateRenderer` | The **entire email-side infrastructure** already handles enqueue, render, dispatch, and dedup. New handler just calls `enqueue()`. |
| `EnvelopeEventsEmailHandler`, `UserEventsConsumer` | Independent handlers on separate topics — no cross-talk. |

Also not touched:
- `Transaction` entity — no new fields, no new methods. `completedAt` already exists and is what we read.
- `Account` / `User` entities — we read `getUser()`, `getEmail()`, `getFistName()` through existing associations (LAZY, fine inside `@Transactional`).
- Database schema — one new topic value, but `@Enumerated(EnumType.STRING)` makes this a non-migration (no schema change needed for MySQL; `VARCHAR(255)` already wide enough).
- Templates directory — 3 new `.html` files under `src/main/resources/templates/email/`, but existing templates remain untouched.

This is the minimum viable surface that satisfies the proposal.
