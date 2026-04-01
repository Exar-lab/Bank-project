## When to Use

- Adding log statements to any class (service, controller, repository, Kafka listener)
- Deciding what level to log at (DEBUG, INFO, WARN, ERROR)
- Logging Kafka events before publishing or after consuming
- Setting up MDC for request tracing across service calls
- Avoiding sensitive data exposure in logs (passwords, tokens, card numbers)

## Critical Patterns

### ✅ Correct Pattern — Logger Declaration

```java
package com.banco.co.account.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    // One static final logger per class, named after the class
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public AccountResponseDto createAccount(CreateAccountDto dto) {
        log.info("Creating account for holder: {}", dto.accountHolder());  // parameterized
        var saved = accountRepository.save(accountMapper.toEntity(dto));
        log.info("Account created successfully with id: {}", saved.getId());
        return accountMapper.toResponseDto(saved);
    }
}
```

### ❌ Common Mistake — String Concatenation in Log

```java
// NEVER DO THIS
log.info("Creating account for holder: " + dto.accountHolder()); // String is built even if INFO is disabled
log.debug("Processing " + items.size() + " items for " + userId); // 3 concatenations always evaluated
```

**Why it's wrong**: String concatenation happens before the log level check. If DEBUG is disabled, you wasted CPU building the string. Use `{}` placeholders — SLF4J evaluates them only if the level is active.

---

### ✅ Correct Pattern — Log Levels

```java
// DEBUG — internal state, algorithm steps, values in dev only
log.debug("JWT validation — claims extracted: sub={}, exp={}", claims.getSubject(), claims.getExpiration());

// INFO — business events that matter in production
log.info("Transaction {} completed: {} {} from account {} to account {}",
    txId, amount, currency, sourceAccountId, destinationAccountId);

// WARN — recoverable issues, degraded behavior, retries
log.warn("Retry {}/{} for Kafka publish of event {} — topic: {}",
    attempt, maxAttempts, eventId, topicName);

// ERROR — failures requiring attention, with exception
log.error("Failed to process transaction {}: {}", transactionId, ex.getMessage(), ex);
```

### ❌ Common Mistake — Wrong Log Level

```java
// NEVER log sensitive or noisy data at INFO
log.info("User login attempt with password: {}", password);  // SECURITY VIOLATION
log.info("Fetching account {}...", id);                      // Too noisy for INFO, use DEBUG
log.error("Account not found");                              // Not an error — expected business case → use WARN or let exception propagate
```

---

### ✅ Correct Pattern — NEVER Log Sensitive Data

```java
// ✅ Safe — log only non-sensitive identifiers
public void processPayment(PaymentDto payment) {
    log.info("Processing payment: amount={}, currency={}, accountId={}",
        payment.amount(), payment.currency(), payment.accountId());
    // NEVER: payment.cardNumber(), payment.cvv(), payment.token()
}

// ✅ Safe — mask card PAN
public void logCardOperation(String cardPan, String operation) {
    String masked = "****-****-****-" + cardPan.substring(cardPan.length() - 4);
    log.info("Card operation '{}' for card ending in {}", operation, masked);
}
```

### ❌ Common Mistake — Logging Sensitive Fields

```java
// NEVER DO ANY OF THESE
log.info("Login request: user={}, password={}", username, password);  // password in logs
log.debug("JWT token issued: {}", jwtToken);                          // token in logs
log.info("Card details: PAN={}, CVV={}", cardPan, cvv);              // PAN+CVV in logs
log.error("Auth failed for: {}", user.toString());                    // toString() may include password hash
```

---

### ✅ Correct Pattern — MDC for Request Tracing

```java
package com.banco.co.security.filter;

import org.slf4j.MDC;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.UUID;

public class MdcRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws java.io.IOException, jakarta.servlet.ServletException {

        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();  // Always clear MDC — prevents leaking to next request on thread pool
        }
    }
}
```

Usage in logback.xml:
```xml
<pattern>%d{ISO8601} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
```

---

### ✅ Correct Pattern — Kafka Event Logging

```java
package com.banco.co.transaction.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);
    private static final String TOPIC = "transaction.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransactionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTransactionCreated(TransactionCreatedEvent event) {
        log.info("Publishing TransactionCreatedEvent: transactionId={}, accountId={}",
            event.transactionId(), event.accountId());  // log BEFORE publish
        kafkaTemplate.send(TOPIC, event.transactionId().toString(), event);
        log.debug("TransactionCreatedEvent sent to topic: {}", TOPIC);
    }
}

// Kafka Consumer logging
@KafkaListener(topics = "transaction.created", groupId = "fraud-service")
public void onTransactionCreated(TransactionCreatedEvent event) {
    log.info("Received TransactionCreatedEvent: transactionId={}", event.transactionId());
    // process...
    log.info("TransactionCreatedEvent processed successfully: transactionId={}", event.transactionId());
}
```

---

### ❌ Common Mistake — printStackTrace()

```java
// NEVER DO THIS
try {
    accountRepository.save(entity);
} catch (Exception e) {
    e.printStackTrace();  // goes to stderr, not to your log aggregator, no context
}
```

**Fix**:
```java
} catch (Exception e) {
    log.error("Failed to save account entity: {}", e.getMessage(), e);  // logs full stack via SLF4J
    throw new AccountPersistenceException("Failed to save account", e);
}
```

## Examples for Banco-Service

- `com.banco.co.account.service.AccountService` — INFO for business events, DEBUG for internals
- `com.banco.co.transaction.messaging.TransactionEventPublisher` — log before Kafka publish
- `com.banco.co.fraud.service.FraudEvaluationService` — WARN for suspicious patterns, ERROR for failures
- `com.banco.co.security.filter.MdcRequestFilter` — MDC setup + cleanup per request
- `com.banco.co.security.service.JwtService` — NEVER log JWT token value, only subject/expiry

## Best Practices

- One `private static final Logger log = LoggerFactory.getLogger(X.class)` per class. Never use a shared logger.
- NEVER use string concatenation in log calls. Always use `{}` parameterized placeholders.
- NEVER log: passwords, JWT tokens, card PANs, CVVs, raw credentials, or personal data fields.
- Log at INFO for meaningful business events (account created, transaction completed, user authenticated).
- Log at DEBUG for development-time internals (claims parsed, cache hit, mapper called).
- Log at WARN for recoverable problems (retry attempt, fallback triggered, deprecated usage).
- Log at ERROR for unrecoverable failures. Always pass the exception as the last argument to capture the stack trace.
- Use MDC in a `OncePerRequestFilter` for correlation IDs. Always `MDC.clear()` in a `finally` block.
- Kafka events: log event ID and key fields BEFORE sending, log confirmation at DEBUG level after.
- No `printStackTrace()`. Ever. Log with SLF4J so the stack trace goes to your log aggregator.
