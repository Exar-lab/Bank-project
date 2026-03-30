package com.banco.co.fraud.adapter;

import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.service.IFraudDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FraudDetectionConsumer guard clauses.
 * Uses Mockito — no Spring context, no Kafka broker.
 */
@ExtendWith(MockitoExtension.class)
class FraudDetectionConsumerTest {

    @Mock
    private IFraudDetectionService fraudDetectionService;

    private FraudDetectionConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new FraudDetectionConsumer(fraudDetectionService, objectMapper);
    }

    // ══════════════════════════════════════════════════════════
    //  Guard: eventType must be "TransactionCompleted"
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_NonTransactionCompletedEventType_DoesNotCallAnalyze() throws Exception {
        String payload = objectMapper.writeValueAsString(buildPayload("AccountCreated", "COMPLETED"));

        consumer.consume(payload);

        verify(fraudDetectionService, never()).analyze(any());
    }

    @Test
    void testConsume_TransactionCreatedEventType_DoesNotCallAnalyze() throws Exception {
        String payload = objectMapper.writeValueAsString(buildPayload("TransactionCreated", "COMPLETED"));

        consumer.consume(payload);

        verify(fraudDetectionService, never()).analyze(any());
    }

    // ══════════════════════════════════════════════════════════
    //  Guard: PENDING_REVIEW / REJECTED status skips re-analysis
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_TransactionCompletedWithPendingReviewStatus_DoesNotCallAnalyze() throws Exception {
        String payload = objectMapper.writeValueAsString(buildPayload("TransactionCompleted", "PENDING_REVIEW"));

        consumer.consume(payload);

        verify(fraudDetectionService, never()).analyze(any());
    }

    @Test
    void testConsume_TransactionCompletedWithRejectedStatus_DoesNotCallAnalyze() throws Exception {
        String payload = objectMapper.writeValueAsString(buildPayload("TransactionCompleted", "REJECTED"));

        consumer.consume(payload);

        verify(fraudDetectionService, never()).analyze(any());
    }

    // ══════════════════════════════════════════════════════════
    //  Happy path: COMPLETED status → analyze() IS called
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_TransactionCompletedWithCompletedStatus_CallsAnalyze() throws Exception {
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.CLEAR);
        String payload = objectMapper.writeValueAsString(buildPayload("TransactionCompleted", "COMPLETED"));

        consumer.consume(payload);

        verify(fraudDetectionService).analyze(any());
    }

    @Test
    void testConsume_TransactionCompletedWithNullStatus_CallsAnalyze() throws Exception {
        when(fraudDetectionService.analyze(any())).thenReturn(FraudAnalysisResult.CLEAR);
        Map<String, Object> payloadMap = buildPayload("TransactionCompleted", null);
        payloadMap.remove("status");
        String payload = objectMapper.writeValueAsString(payloadMap);

        consumer.consume(payload);

        verify(fraudDetectionService).analyze(any());
    }

    // ══════════════════════════════════════════════════════════
    //  Malformed JSON — should not throw, should log and return
    // ══════════════════════════════════════════════════════════

    @Test
    void testConsume_MalformedJson_DoesNotCallAnalyzeAndDoesNotThrow() {
        consumer.consume("{ not valid json !!!");

        verify(fraudDetectionService, never()).analyze(any());
    }

    // ══════════════════════════════════════════════════════════
    //  Helper
    // ══════════════════════════════════════════════════════════

    private Map<String, Object> buildPayload(String eventType, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("transactionId", "tx-unit-001");
        payload.put("transactionCode", "TXN-BCR-001");
        payload.put("fromAccount", "ACC-001");
        payload.put("toAccount", "ACC-002");
        payload.put("amount", new BigDecimal("500000"));
        payload.put("currency", "CRC");
        if (status != null) {
            payload.put("status", status);
        }
        return payload;
    }
}
