package com.banco.co.fraud.service;

import com.banco.co.fraud.config.FraudDetectionProperties;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FraudDetectionServiceImplTest {

    private FraudDetectionServiceImpl service;

    @BeforeEach
    void setUp() {
        FraudDetectionProperties properties = new FraudDetectionProperties(
                new BigDecimal("10000000"),
                new BigDecimal("50000000")
        );
        service = new FraudDetectionServiceImpl(properties);
    }

    @Test
    void testAnalyze_AmountBelowSuspiciousThreshold_ReturnsClear() {
        TransactionFraudContext context = new TransactionFraudContext(
                "tx-001", "ACC-001", "ACC-002",
                new BigDecimal("9999999"), "COP",
                "TXN-TEST-001", "TRANSFER", "WEB",
                null, null, null, null, null
        );
        assertEquals(FraudAnalysisResult.CLEAR, service.analyze(context));
    }

    @Test
    void testAnalyze_AmountEqualsSuspiciousThreshold_ReturnsSuspicious() {
        TransactionFraudContext context = new TransactionFraudContext(
                "tx-002", "ACC-001", "ACC-002",
                new BigDecimal("10000000"), "COP",
                "TXN-TEST-002", "TRANSFER", "WEB",
                null, null, null, null, null
        );
        assertEquals(FraudAnalysisResult.SUSPICIOUS, service.analyze(context));
    }

    @Test
    void testAnalyze_AmountBetweenThresholds_ReturnsSuspicious() {
        TransactionFraudContext context = new TransactionFraudContext(
                "tx-003", "ACC-001", "ACC-002",
                new BigDecimal("25000000"), "COP",
                "TXN-TEST-003", "TRANSFER", "WEB",
                null, null, null, null, null
        );
        assertEquals(FraudAnalysisResult.SUSPICIOUS, service.analyze(context));
    }

    @Test
    void testAnalyze_AmountEqualsBlockedThreshold_ReturnsBlocked() {
        TransactionFraudContext context = new TransactionFraudContext(
                "tx-004", "ACC-001", "ACC-002",
                new BigDecimal("50000000"), "COP",
                "TXN-TEST-004", "TRANSFER", "WEB",
                null, null, null, null, null
        );
        assertEquals(FraudAnalysisResult.BLOCKED, service.analyze(context));
    }

    @Test
    void testAnalyze_AmountAboveBlockedThreshold_ReturnsBlocked() {
        TransactionFraudContext context = new TransactionFraudContext(
                "tx-005", "ACC-001", "ACC-002",
                new BigDecimal("100000000"), "COP",
                "TXN-TEST-005", "TRANSFER", "WEB",
                null, null, null, null, null
        );
        assertEquals(FraudAnalysisResult.BLOCKED, service.analyze(context));
    }
}
