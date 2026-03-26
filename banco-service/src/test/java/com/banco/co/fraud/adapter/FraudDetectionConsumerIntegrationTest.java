package com.banco.co.fraud.adapter;

import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.service.IFraudDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"banco.transaction.events"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9093",
                "port=9093"
        }
)
@DirtiesContext
class FraudDetectionConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IFraudDetectionService fraudDetectionService;

    @Test
    void testConsume_TransactionCompletedEvent_CallsFraudAnalysis() throws Exception {
        when(fraudDetectionService.analyze(any(TransactionFraudContext.class)))
                .thenReturn(FraudAnalysisResult.CLEAR);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "TransactionCompleted");
        payload.put("transactionId", "tx-integration-001");
        payload.put("fromAccount", "ACC-001");
        payload.put("toAccount", "ACC-002");
        payload.put("amount", new BigDecimal("500000"));
        payload.put("currency", "COP");

        kafkaTemplate.send("banco.transaction.events", objectMapper.writeValueAsString(payload));

        TimeUnit.SECONDS.sleep(3);

        verify(fraudDetectionService).analyze(any(TransactionFraudContext.class));
    }

    @Test
    void testConsume_NonTransactionCompletedEvent_SkipsAnalysis() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "AccountCreated");
        payload.put("transactionId", "tx-integration-002");

        kafkaTemplate.send("banco.transaction.events", objectMapper.writeValueAsString(payload));

        TimeUnit.SECONDS.sleep(2);

        verifyNoInteractions(fraudDetectionService);
    }
}
