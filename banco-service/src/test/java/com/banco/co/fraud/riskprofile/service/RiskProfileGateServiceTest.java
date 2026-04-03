package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.config.FraudDetectionProperties;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.riskprofile.dto.RiskProfileSnapshot;
import com.banco.co.fraud.riskprofile.enums.RiskProfileFallbackPolicy;
import com.banco.co.fraud.riskprofile.enums.RiskTier;
import com.banco.co.fraud.riskprofile.port.IRiskProfileQueryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskProfileGateServiceTest {

    private IRiskProfileQueryPort riskProfileQueryPort;
    private RiskProfileGateService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        riskProfileQueryPort = mock(IRiskProfileQueryPort.class);
        meterRegistry = new SimpleMeterRegistry();
        FraudDetectionProperties properties = new FraudDetectionProperties(
                true,
                new BigDecimal("10000000"),
                new BigDecimal("50000000"),
                25,
                RiskProfileFallbackPolicy.FAIL_OPEN_LEGACY_THRESHOLD
        );
        service = new RiskProfileGateService(riskProfileQueryPort, properties, meterRegistry);
    }

    @Test
    void testEvaluate_HighTier_Blocked() {
        TransactionFraudContext context = context(new BigDecimal("1000"));
        when(riskProfileQueryPort.findByAccountCode("ACC-001"))
                .thenReturn(Optional.of(snapshot(RiskTier.HIGH, new BigDecimal("90.00"))));

        FraudAnalysisResult result = service.evaluate(context);

        assertEquals(FraudAnalysisResult.BLOCKED, result);
    }

    @Test
    void testEvaluate_MediumTier_Suspicious() {
        TransactionFraudContext context = context(new BigDecimal("1000"));
        when(riskProfileQueryPort.findByAccountCode("ACC-001"))
                .thenReturn(Optional.of(snapshot(RiskTier.MEDIUM, new BigDecimal("60.00"))));

        FraudAnalysisResult result = service.evaluate(context);

        assertEquals(FraudAnalysisResult.SUSPICIOUS, result);
    }

    @Test
    void testEvaluate_NoProfile_FallsBackToAmountThresholds() {
        TransactionFraudContext context = context(new BigDecimal("50000000"));
        when(riskProfileQueryPort.findByAccountCode("ACC-001"))
                .thenReturn(Optional.empty());

        FraudAnalysisResult result = service.evaluate(context);

        assertEquals(FraudAnalysisResult.BLOCKED, result);
    }

    @Test
    void testEvaluate_StaleHighRiskProfile_StillBlocksTransaction() {
        TransactionFraudContext context = context(new BigDecimal("1000"));
        when(riskProfileQueryPort.findByAccountCode("ACC-001"))
                .thenReturn(Optional.of(new RiskProfileSnapshot(
                        "ACC-001",
                        RiskTier.HIGH,
                        new BigDecimal("92.00"),
                        7L,
                        "v1",
                        Instant.now().minus(30, ChronoUnit.DAYS)
                )));

        FraudAnalysisResult result = service.evaluate(context);

        assertEquals(FraudAnalysisResult.BLOCKED, result);
    }

    @Test
    void testEvaluate_QueryTimeout_AppliesFailOpenFallbackAndIncrementsTimeoutMetric() {
        FraudDetectionProperties timeoutProperties = new FraudDetectionProperties(
                true,
                new BigDecimal("10000000"),
                new BigDecimal("50000000"),
                5,
                RiskProfileFallbackPolicy.FAIL_OPEN_LEGACY_THRESHOLD
        );
        RiskProfileGateService timeoutService = new RiskProfileGateService(
                riskProfileQueryPort,
                timeoutProperties,
                meterRegistry
        );

        when(riskProfileQueryPort.findByAccountCode("ACC-001"))
                .thenAnswer(invocation -> {
                    CountDownLatch latch = new CountDownLatch(1);
                    latch.await();
                    return Optional.empty();
                });

        FraudAnalysisResult result = timeoutService.evaluate(context(new BigDecimal("12000000")));

        assertEquals(FraudAnalysisResult.SUSPICIOUS, result);
        assertEquals(
                1.0,
                meterRegistry.counter("fraud.riskprofile.gate.fallback", "reason", "timeout").count()
        );
    }

    @Test
    void testEvaluate_NormalPath_LatencyUnderFiftyMillisecondsAndTimerRecorded() {
        when(riskProfileQueryPort.findByAccountCode("ACC-001"))
                .thenReturn(Optional.of(snapshot(RiskTier.LOW, new BigDecimal("10.00"))));

        long start = System.nanoTime();
        FraudAnalysisResult result = service.evaluate(context(new BigDecimal("1000")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(FraudAnalysisResult.CLEAR, result);
        assertTrue(elapsedMs < 50, "Expected gate latency < 50ms but was " + elapsedMs + "ms");
        assertTrue(meterRegistry.timer("fraud.riskprofile.gate.latency").count() >= 1);
    }

    private TransactionFraudContext context(BigDecimal amount) {
        return new TransactionFraudContext(
                "tx-001",
                "ACC-001",
                "ACC-002",
                amount,
                "CRC",
                "TXN-001",
                "TRANSFER",
                "WEB",
                null,
                null,
                null,
                null,
                null
        );
    }

    private RiskProfileSnapshot snapshot(RiskTier tier, BigDecimal score) {
        return new RiskProfileSnapshot(
                "ACC-001",
                tier,
                score,
                2L,
                "v1",
                Instant.now()
        );
    }
}
