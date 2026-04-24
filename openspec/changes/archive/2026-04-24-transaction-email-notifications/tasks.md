# Tasks: Transaction Email Notifications

## Phase 1: Foundation

- [x] 1.1 Add `TRANSACTION_NOTIFICATION_EVENTS("banco.transaction.notification.events")` to `com.banco.co.outbox.enums.KafkaTopic`
- [x] 1.2 Create `TransferSenderEmailContext` record in `com.banco.co.notification.email.dto` (9 fields per FR-020)
- [x] 1.3 Create `TransferReceiverEmailContext` record in `com.banco.co.notification.email.dto` (9 fields per FR-021)
- [x] 1.4 Create `AccountOperationEmailContext` record in `com.banco.co.notification.email.dto` (9 fields per FR-022)

## Phase 2: TransactionService — Publisher

- [x] 2.1 Add private `buildNotificationPayload(Transaction)` to `TransactionService`; serialize via injected `ObjectMapper`; populate `fromAccount`/`toAccount` per type; use `completedAt → processedAt → now()` for `occurredAt`
- [x] 2.2 Remove 4-arg `buildTransactionPayload` overload; migrate `cashDeposit`, `checkDeposit`, `cashWithdrawal` call sites to 2-arg variant (FR-010)
- [x] 2.3 Add second `outboxEventPort.save(TRANSACTION_NOTIFICATION_EVENTS, buildNotificationPayload(...))` in TRANSFER COMPLETED path — `transfer()` + COMPLETED branch of `approveTransaction()`
- [x] 2.4 Add second `outboxEventPort.save(...)` to PAYMENT completed paths — `payment()` + `payService()`
- [x] 2.5 Add second `outboxEventPort.save(...)` to DEPOSIT completed paths — `cashDeposit()` + `checkDeposit()`
- [x] 2.6 Add second `outboxEventPort.save(...)` to WITHDRAWAL completed path — `cashWithdrawal()`

## Phase 3: Consumer + Templates

- [x] 3.1 Create `TransactionEventsEmailHandler` in `com.banco.co.notification.email.adapter.in`; `@KafkaListener(topics = "banco.transaction.notification.events", groupId = "banco-service-group")`; parse with `ObjectMapper`; silent skip on unknown `eventType` (FR-017); WARN + return on malformed JSON (FR-018)
- [x] 3.2 Implement TRANSFER routing: different `userId` → enqueue x2 (`{txId}:sender`, `{txId}:receiver`, templates `transaction-transfer-sender` + `transaction-transfer-receiver`); same `userId` → enqueue x1 sender only (FR-012, FR-013)
- [x] 3.3 Implement DEPOSIT/WITHDRAWAL/PAYMENT routing: enqueue x1 to respective account owner, `eventId={txId}:operation`, template `email/transaction-account-operation` (FR-014–FR-016)
- [x] 3.4 Create `src/main/resources/templates/email/transaction-transfer-sender.html` Thymeleaf template
- [x] 3.5 Create `src/main/resources/templates/email/transaction-transfer-receiver.html` Thymeleaf template
- [x] 3.6 Create `src/main/resources/templates/email/transaction-account-operation.html` Thymeleaf template; include `operationLabel` display

## Phase 4: Tests

- [x] 4.1 `TransactionServiceTest` — TS-001: `testTransfer_CompletedClearFraud_EmitsDomainAndNotificationOutboxEvents` — `outboxEventPort.save` x2; call#2 topic=TRANSACTION_NOTIFICATION_EVENTS, non-null `fromAccount`+`toAccount` with `userEmail`
- [x] 4.2 `TransactionServiceTest` — TS-002/TS-003/TS-004: `cashDeposit`, `cashWithdrawal`, `payment` each emit x2 saves; notification payload `fromAccount`/`toAccount` null per type
- [x] 4.3 `TransactionServiceTest` — TS-005/TS-006: `reverseTransaction` + BLOCKED fraud → `save` x1 only; `TRANSACTION_NOTIFICATION_EVENTS` never used
- [x] 4.4 `TransactionEventsEmailHandlerTest` — TS-007/TS-008: TRANSFER different users → `enqueue` x2; same user → `enqueue` x1 sender only
- [x] 4.5 `TransactionEventsEmailHandlerTest` — TS-009/TS-010/TS-011: DEPOSIT, WITHDRAWAL, PAYMENT → `enqueue` x1 each, correct `eventId` + template
- [x] 4.6 `TransactionEventsEmailHandlerTest` — TS-012/TS-013: unknown `eventType` → no enqueue, no exception; malformed JSON → WARN logged, no enqueue, no exception
- [x] 4.7 Idempotency TS-014: consume same payload twice → 1 row per `eventId` in `email_outbox_events`; no exception propagates (Docker-gated — requires Testcontainers)
- [x] 4.8 DTO tests TS-015/TS-016: `TransferSenderEmailContext` all fields round-trip; DEPOSIT context has non-blank `operationLabel`
