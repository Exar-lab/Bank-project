package com.banco.co.fraud.riskprofile.adapter;

import com.banco.co.fraud.riskprofile.dto.TransactionCompletedRiskEvent;
import com.banco.co.fraud.riskprofile.service.RiskProfileAsyncUpdaterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RiskProfileConsumer {

    private static final Logger log = LoggerFactory.getLogger(RiskProfileConsumer.class);
    private static final String TRANSACTION_COMPLETED = "TransactionCompleted";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_REJECTED = "REJECTED";

    private final ObjectMapper objectMapper;
    private final RiskProfileAsyncUpdaterService asyncUpdaterService;
    private final Counter processedCounter;
    private final Counter duplicatedCounter;
    private final Counter invalidPayloadCounter;
    private final Counter failedCounter;

    public RiskProfileConsumer(ObjectMapper objectMapper,
                               RiskProfileAsyncUpdaterService asyncUpdaterService,
                               MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.asyncUpdaterService = asyncUpdaterService;
        this.processedCounter = meterRegistry.counter("fraud.riskprofile.async.events", "outcome", "processed");
        this.duplicatedCounter = meterRegistry.counter("fraud.riskprofile.async.events", "outcome", "duplicated");
        this.invalidPayloadCounter = meterRegistry.counter("fraud.riskprofile.async.events", "outcome", "invalid_payload");
        this.failedCounter = meterRegistry.counter("fraud.riskprofile.async.events", "outcome", "failed");
    }

    @KafkaListener(
            topics = "banco.transaction.events",
            groupId = "banco-risk-profile-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload String payload) {
        // Policy: malformed payloads are non-processable and MUST be skipped (no retry/DLT).
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception ex) {
            invalidPayloadCounter.increment();
            log.warn("event=risk_profile_async_invalid_payload reason=malformed_json action=skip raw={}", payload, ex);
            return;
        }

        String eventType = node.path("eventType").asText();
        if (!TRANSACTION_COMPLETED.equals(eventType)) {
            return;
        }

        String status = node.path("status").asText(null);
        if (STATUS_PENDING_REVIEW.equals(status) || STATUS_REJECTED.equals(status)) {
            return;
        }

        String eventId = resolveEventId(node);
        String transactionId = node.path("transactionId").asText(null);
        String accountCode = resolveAccountCode(node);

        if (eventId == null || eventId.isBlank() || accountCode == null || accountCode.isBlank()) {
            invalidPayloadCounter.increment();
            log.warn("event=risk_profile_async_invalid_payload reason=missing_fields action=skip eventId={} accountCode={}",
                    eventId, accountCode);
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(node.path("amount").asText("0"));
        } catch (NumberFormatException ex) {
            invalidPayloadCounter.increment();
            log.warn("event=risk_profile_async_invalid_payload reason=invalid_amount action=skip eventId={} transactionId={} rawAmount={}",
                    eventId, transactionId, node.path("amount").asText(null));
            return;
        }

        TransactionCompletedRiskEvent event = new TransactionCompletedRiskEvent(
                eventId,
                transactionId,
                accountCode,
                amount,
                status
        );

        try {
            boolean processed = asyncUpdaterService.updateFromTransactionCompleted(event);
            if (!processed) {
                duplicatedCounter.increment();
                log.info("event=risk_profile_async_duplicated action=skip eventId={} transactionId={} accountCode={}",
                        event.eventId(), event.transactionId(), event.accountCode());
                return;
            }
            processedCounter.increment();
            log.info("event=risk_profile_async_processed action=upserted eventId={} transactionId={} accountCode={} status={} amount={}",
                    event.eventId(), event.transactionId(), event.accountCode(), event.status(), event.amount());
        } catch (RuntimeException ex) {
            // Policy: processable/transient failures MUST propagate to trigger retry and DLT.
            failedCounter.increment();
            log.error("event=risk_profile_async_failed action=retry eventId={} transactionId={} accountCode={}",
                    event.eventId(), event.transactionId(), event.accountCode(), ex);
            throw new IllegalStateException("Failed processing risk profile async event " + event.eventId(), ex);
        }
    }

    private String resolveEventId(JsonNode node) {
        String explicitEventId = node.path("eventId").asText(null);
        if (explicitEventId != null && !explicitEventId.isBlank()) {
            return explicitEventId;
        }
        return node.path("transactionId").asText(null);
    }

    private String resolveAccountCode(JsonNode node) {
        String fromAccountCode = node.path("fromAccountCode").asText(null);
        if (fromAccountCode != null && !fromAccountCode.isBlank()) {
            return fromAccountCode;
        }
        return node.path("fromAccount").asText(null);
    }
}
