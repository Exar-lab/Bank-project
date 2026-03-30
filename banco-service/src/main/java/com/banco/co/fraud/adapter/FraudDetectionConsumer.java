package com.banco.co.fraud.adapter;

import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.service.IFraudDetectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FraudDetectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionConsumer.class);
    private static final String TRANSACTION_COMPLETED = "TransactionCompleted";

    private final IFraudDetectionService fraudDetectionService;
    private final ObjectMapper objectMapper;

    public FraudDetectionConsumer(IFraudDetectionService fraudDetectionService,
                                   ObjectMapper objectMapper) {
        this.fraudDetectionService = fraudDetectionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "banco.transaction.events",
            groupId = "banco-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("FraudDetectionConsumer: failed to parse payload. Raw: {}", payload, e);
            return; // malformed JSON — skip, do not retry
        }

        String eventType = node.path("eventType").asText();
        if (!TRANSACTION_COMPLETED.equals(eventType)) {
            log.debug("FraudDetectionConsumer: ignoring event type '{}'", eventType);
            return;
        }

        String transactionStatus = node.path("status").asText(null);
        if ("PENDING_REVIEW".equals(transactionStatus) || "REJECTED".equals(transactionStatus)) {
            log.debug("FraudDetectionConsumer: skipping re-analysis for already-handled status='{}', transactionCode='{}'",
                    transactionStatus, node.path("transactionCode").asText("unknown"));
            return;
        }

        TransactionFraudContext context = new TransactionFraudContext(
                node.path("transactionId").asText(),
                node.path("fromAccount").asText(),
                node.path("toAccount").asText(null),
                new BigDecimal(node.path("amount").asText()),
                node.path("currency").asText(),
                node.path("transactionCode").asText(null),
                node.path("transactionType").asText(null),
                node.path("channel").asText(null),
                node.path("merchantName").asText(null),
                node.path("merchantMccCode").asText(null),
                node.path("ipAddress").asText(null),
                node.path("deviceId").asText(null),
                node.path("locationCountry").asText(null)
        );

        // Let RuntimeException propagate → DefaultErrorHandler will retry up to 3 times
        FraudAnalysisResult result = fraudDetectionService.analyze(context);
        log.info("FraudDetectionConsumer: transactionId={}, result={}", context.transactionId(), result);
    }
}
