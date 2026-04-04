package com.banco.co.fraud.service;

import com.banco.co.fraud.config.FraudDetectionProperties;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.riskprofile.enums.RiskProfileFallbackPolicy;
import com.banco.co.fraud.riskprofile.service.IRiskProfileGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FraudDetectionServiceImplTest {

    private FraudDetectionServiceImpl service;
    private IRiskProfileGateService riskProfileGateService;

    @BeforeEach
    void setUp() {
        riskProfileGateService = Mockito.mock(IRiskProfileGateService.class);
        FraudDetectionProperties properties = new FraudDetectionProperties(
                false,
                new BigDecimal("10000000"),
                new BigDecimal("50000000"),
                25,
                RiskProfileFallbackPolicy.FAIL_OPEN_LEGACY_THRESHOLD
        );
        service = new FraudDetectionServiceImpl(properties, riskProfileGateService);
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

    @Test
    void testAnalyze_RiskProfileEnabled_DelegatesToRiskProfileGate() {
        FraudDetectionProperties properties = new FraudDetectionProperties(
                true,
                new BigDecimal("10000000"),
                new BigDecimal("50000000"),
                25,
                RiskProfileFallbackPolicy.FAIL_OPEN_LEGACY_THRESHOLD
        );
        FraudDetectionServiceImpl riskProfileEnabledService =
                new FraudDetectionServiceImpl(properties, riskProfileGateService);

        TransactionFraudContext context = new TransactionFraudContext(
                "tx-006", "ACC-001", "ACC-002",
                new BigDecimal("1"), "CRC",
                "TXN-TEST-006", "TRANSFER", "WEB",
                null, null, null, null, null
        );

        Mockito.when(riskProfileGateService.evaluate(context)).thenReturn(FraudAnalysisResult.SUSPICIOUS);

        FraudAnalysisResult result = riskProfileEnabledService.analyze(context);

        assertEquals(FraudAnalysisResult.SUSPICIOUS, result);
        Mockito.verify(riskProfileGateService).evaluate(context);
    }
}
