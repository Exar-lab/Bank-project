package com.banco.co.notification.email.adapter.in;

import com.banco.co.notification.email.service.IEmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TransactionEventsEmailHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventsEmailHandler.class);
    private static final String TRANSACTION_COMPLETED_NOTIFICATION = "TransactionCompletedNotification";
    private static final String BANK_NAME = "Banco CO";

    private final ObjectMapper objectMapper;
    private final IEmailService emailService;

    public TransactionEventsEmailHandler(ObjectMapper objectMapper, IEmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @KafkaListener(
            topics = "banco.transaction.notification.events",
            groupId = "banco-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(@Payload String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            log.warn("TransactionEventsEmailHandler: malformed payload (length={})", payload.length());
            return;
        }

        String eventType = node.path("eventType").asText();
        if (!TRANSACTION_COMPLETED_NOTIFICATION.equals(eventType)) {
            return;
        }

        String txId            = node.path("transactionId").asText();
        String type            = node.path("type").asText();
        BigDecimal amount      = new BigDecimal(node.path("amount").asText());
        String currency        = node.path("currency").asText();
        String transactionCode = node.path("transactionCode").asText();
        LocalDateTime occurredAt = LocalDateTime.parse(node.path("occurredAt").asText());

        JsonNode fromNode = node.path("fromAccount");
        JsonNode toNode   = node.path("toAccount");

        switch (type) {
            case "TRANSFER"   -> handleTransfer(txId, amount, currency, transactionCode, occurredAt, fromNode, toNode);
            case "DEPOSIT"    -> handleOperation(txId, amount, currency, transactionCode, occurredAt, toNode,   "DEPOSIT",    "Depósito");
            case "WITHDRAWAL" -> handleOperation(txId, amount, currency, transactionCode, occurredAt, fromNode, "WITHDRAWAL", "Retiro");
            case "PAYMENT"    -> handleOperation(txId, amount, currency, transactionCode, occurredAt, fromNode, "PAYMENT",    "Pago");
            default           -> log.warn("TransactionEventsEmailHandler: unhandled type={} for txId={}", type, txId);
        }
    }

    private void handleTransfer(String txId, BigDecimal amount, String currency,
                                 String transactionCode, LocalDateTime occurredAt,
                                 JsonNode fromNode, JsonNode toNode) {
        String fromUserId      = fromNode.path("userId").asText();
        String fromEmail       = fromNode.path("userEmail").asText();
        String fromName        = fromNode.path("userFirstName").asText();
        String fromAccountCode = fromNode.path("accountCode").asText();

        String toUserId      = toNode.path("userId").asText();
        String toEmail       = toNode.path("userEmail").asText();
        String toName        = toNode.path("userFirstName").asText();
        String toAccountCode = toNode.path("accountCode").asText();

        emailService.enqueue(
                txId + ":sender",
                UUID.fromString(fromUserId),
                fromEmail,
                fromName,
                "email/transaction-transfer-sender",
                buildTransferSenderContext(fromName, amount, currency, toName, toAccountCode, fromAccountCode, transactionCode, occurredAt),
                "Transferencia completada"
        );

        if (!fromUserId.equals(toUserId)) {
            emailService.enqueue(
                    txId + ":receiver",
                    UUID.fromString(toUserId),
                    toEmail,
                    toName,
                    "email/transaction-transfer-receiver",
                    buildTransferReceiverContext(toName, amount, currency, fromName, fromAccountCode, toAccountCode, transactionCode, occurredAt),
                    "Has recibido una transferencia"
            );
        }

        log.info("TransactionEventsEmailHandler: TRANSFER emails enqueued for txId={}", txId);
    }

    private void handleOperation(String txId, BigDecimal amount, String currency,
                                  String transactionCode, LocalDateTime occurredAt,
                                  JsonNode accountNode, String operationType, String operationLabel) {
        String userId      = accountNode.path("userId").asText();
        String email       = accountNode.path("userEmail").asText();
        String name        = accountNode.path("userFirstName").asText();
        String accountCode = accountNode.path("accountCode").asText();

        emailService.enqueue(
                txId + ":operation",
                UUID.fromString(userId),
                email,
                name,
                "email/transaction-account-operation",
                buildOperationContext(name, operationType, operationLabel, amount, currency, accountCode, transactionCode, occurredAt),
                operationLabel + " completado"
        );

        log.info("TransactionEventsEmailHandler: {} email enqueued for txId={}", operationType, txId);
    }

    private Map<String, Object> buildTransferSenderContext(String recipientName, BigDecimal amount, String currency,
                                                            String counterpartyName, String counterpartyAccountCode,
                                                            String fromAccountCode, String transactionCode,
                                                            LocalDateTime occurredAt) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("recipientName", recipientName);
        ctx.put("amount", amount.toPlainString());
        ctx.put("currency", currency);
        ctx.put("counterpartyName", counterpartyName);
        ctx.put("counterpartyAccountCode", counterpartyAccountCode);
        ctx.put("fromAccountCode", fromAccountCode);
        ctx.put("transactionCode", transactionCode);
        ctx.put("occurredAt", occurredAt.toString());
        ctx.put("bankName", BANK_NAME);
        return ctx;
    }

    private Map<String, Object> buildTransferReceiverContext(String recipientName, BigDecimal amount, String currency,
                                                              String counterpartyName, String counterpartyAccountCode,
                                                              String toAccountCode, String transactionCode,
                                                              LocalDateTime occurredAt) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("recipientName", recipientName);
        ctx.put("amount", amount.toPlainString());
        ctx.put("currency", currency);
        ctx.put("counterpartyName", counterpartyName);
        ctx.put("counterpartyAccountCode", counterpartyAccountCode);
        ctx.put("toAccountCode", toAccountCode);
        ctx.put("transactionCode", transactionCode);
        ctx.put("occurredAt", occurredAt.toString());
        ctx.put("bankName", BANK_NAME);
        return ctx;
    }

    private Map<String, Object> buildOperationContext(String recipientName, String operationType,
                                                       String operationLabel, BigDecimal amount, String currency,
                                                       String accountCode, String transactionCode,
                                                       LocalDateTime occurredAt) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("recipientName", recipientName);
        ctx.put("operationType", operationType);
        ctx.put("operationLabel", operationLabel);
        ctx.put("amount", amount.toPlainString());
        ctx.put("currency", currency);
        ctx.put("accountCode", accountCode);
        ctx.put("transactionCode", transactionCode);
        ctx.put("occurredAt", occurredAt.toString());
        ctx.put("bankName", BANK_NAME);
        return ctx;
    }
}
