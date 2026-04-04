package com.banco.co.fraud.riskprofile.adapter;

import com.banco.co.fraud.riskprofile.dto.TransactionCompletedRiskEvent;
import com.banco.co.fraud.riskprofile.service.IRiskProfileAsyncUpdaterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskProfileConsumerTest {

    @Mock
    private IRiskProfileAsyncUpdaterService asyncUpdaterService;

    private RiskProfileConsumer consumer;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        consumer = new RiskProfileConsumer(objectMapper, asyncUpdaterService, meterRegistry);
    }

    @Test
    void testConsume_TransactionCompletedEvent_CallsUpdater() throws Exception {
        when(asyncUpdaterService.updateFromTransactionCompleted(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        String payload = objectMapper.writeValueAsString(buildPayload("TransactionCompleted", "COMPLETED"));

        consumer.consume(payload);

        ArgumentCaptor<TransactionCompletedRiskEvent> captor = ArgumentCaptor.forClass(TransactionCompletedRiskEvent.class);
        verify(asyncUpdaterService).updateFromTransactionCompleted(captor.capture());
        assertEquals("tx-001", captor.getValue().eventId());
        assertEquals("ACC-001", captor.getValue().accountCode());
        assertEquals(new BigDecimal("50000000"), captor.getValue().amount());
        assertEquals(1.0, meterRegistry.get("fraud.riskprofile.async.events").tag("outcome", "processed").counter().count());
    }

    @Test
    void testConsume_DuplicatedEventId_StillDelegatesAndUpdaterHandlesDedup() throws Exception {
        when(asyncUpdaterService.updateFromTransactionCompleted(org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        Map<String, Object> payloadMap = buildPayload("TransactionCompleted", "COMPLETED");
        payloadMap.put("eventId", "evt-123");

        consumer.consume(objectMapper.writeValueAsString(payloadMap));

        ArgumentCaptor<TransactionCompletedRiskEvent> captor = ArgumentCaptor.forClass(TransactionCompletedRiskEvent.class);
        verify(asyncUpdaterService).updateFromTransactionCompleted(captor.capture());
        assertEquals("evt-123", captor.getValue().eventId());
        assertEquals(1.0, meterRegistry.get("fraud.riskprofile.async.events").tag("outcome", "duplicated").counter().count());
    }

    @Test
    void testConsume_NonTransactionCompletedEvent_DoesNotCallUpdater() throws Exception {
        String payload = objectMapper.writeValueAsString(buildPayload("AccountCreated", "COMPLETED"));

        consumer.consume(payload);

        verify(asyncUpdaterService, never()).updateFromTransactionCompleted(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testConsume_MalformedJson_DoesNotCallUpdater() {
        consumer.consume("{ invalid-json }");

        verify(asyncUpdaterService, never()).updateFromTransactionCompleted(org.mockito.ArgumentMatchers.any());
        assertEquals(1.0, meterRegistry.get("fraud.riskprofile.async.events").tag("outcome", "invalid_payload").counter().count());
    }

    @Test
    void testConsume_InvalidAmount_DoesNotCallUpdater() throws Exception {
        Map<String, Object> payloadMap = buildPayload("TransactionCompleted", "COMPLETED");
        payloadMap.put("amount", "invalid-number");

        consumer.consume(objectMapper.writeValueAsString(payloadMap));

        verify(asyncUpdaterService, never()).updateFromTransactionCompleted(org.mockito.ArgumentMatchers.any());
        assertEquals(1.0, meterRegistry.get("fraud.riskprofile.async.events").tag("outcome", "invalid_payload").counter().count());
    }

    @Test
    void testConsume_UpdaterFails_ThrowsRetryableException() throws Exception {
        when(asyncUpdaterService.updateFromTransactionCompleted(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("db down"));

        String payload = objectMapper.writeValueAsString(buildPayload("TransactionCompleted", "COMPLETED"));

        assertThrows(IllegalStateException.class, () -> consumer.consume(payload));
        assertEquals(1.0, meterRegistry.get("fraud.riskprofile.async.events").tag("outcome", "failed").counter().count());
    }

    private Map<String, Object> buildPayload(String eventType, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("transactionId", "tx-001");
        payload.put("fromAccountCode", "ACC-001");
        payload.put("amount", new BigDecimal("50000000"));
        payload.put("currency", "CRC");
        payload.put("status", status);
        return payload;
    }
}
