package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.config.FraudDetectionProperties;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.riskprofile.dto.RiskProfileSnapshot;
import com.banco.co.fraud.riskprofile.enums.RiskProfileFallbackPolicy;
import com.banco.co.fraud.riskprofile.enums.RiskTier;
import com.banco.co.fraud.riskprofile.port.IRiskProfileQueryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class RiskProfileGateService implements IRiskProfileGateService {

    private static final Logger log = LoggerFactory.getLogger(RiskProfileGateService.class);

    private final IRiskProfileQueryPort riskProfileQueryPort;
    private final FraudDetectionProperties properties;
    private final Counter timeoutFallbackCounter;
    private final Counter errorFallbackCounter;
    private final Timer gateLatencyTimer;

    public RiskProfileGateService(
            IRiskProfileQueryPort riskProfileQueryPort,
            FraudDetectionProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.riskProfileQueryPort = riskProfileQueryPort;
        this.properties = properties;
        this.timeoutFallbackCounter = meterRegistry.counter("fraud.riskprofile.gate.fallback", "reason", "timeout");
        this.errorFallbackCounter = meterRegistry.counter("fraud.riskprofile.gate.fallback", "reason", "error");
        this.gateLatencyTimer = meterRegistry.timer("fraud.riskprofile.gate.latency");
    }

    @Override
    public FraudAnalysisResult evaluate(TransactionFraudContext context) {
        long start = System.nanoTime();

        String accountCode = context.fromAccount();
        try {
            if (accountCode == null || accountCode.isBlank()) {
                return evaluateByAmount(context.amount());
            }

            Optional<RiskProfileSnapshot> profile = findProfileWithTimeout(accountCode);
            return profile
                    .map(snapshot -> evaluateWithProfile(context.amount(), snapshot))
                    .orElseGet(() -> applyFallbackPolicy(context.amount(), accountCode));
        } finally {
            gateLatencyTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private Optional<RiskProfileSnapshot> findProfileWithTimeout(String accountCode) {
        CompletableFuture<Optional<RiskProfileSnapshot>> asyncQuery = CompletableFuture.supplyAsync(
                () -> riskProfileQueryPort.findByAccountCode(accountCode)
        );

        long timeoutMs = Math.max(1L, properties.riskProfileQueryTimeoutMs());
        try {
            return asyncQuery.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            asyncQuery.cancel(true);
            timeoutFallbackCounter.increment();
            log.warn(
                    "Risk profile gate timeout accountCode={} timeoutMs={} fallbackPolicy={}",
                    accountCode,
                    timeoutMs,
                    properties.riskProfileFallbackPolicy()
            );
            return Optional.empty();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            errorFallbackCounter.increment();
            log.warn(
                    "Risk profile gate interrupted accountCode={} fallbackPolicy={}",
                    accountCode,
                    properties.riskProfileFallbackPolicy(),
                    interruptedException
            );
            return Optional.empty();
        } catch (ExecutionException | RuntimeException queryException) {
            errorFallbackCounter.increment();
            log.warn(
                    "Risk profile gate error accountCode={} fallbackPolicy={} message={}",
                    accountCode,
                    properties.riskProfileFallbackPolicy(),
                    queryException.getMessage()
            );
            return Optional.empty();
        }
    }

    private FraudAnalysisResult applyFallbackPolicy(BigDecimal amount, String accountCode) {
        RiskProfileFallbackPolicy fallbackPolicy = properties.riskProfileFallbackPolicy();
        return switch (fallbackPolicy) {
            case FAIL_OPEN_LEGACY_THRESHOLD -> {
                log.info(
                        "Risk profile gate fallback applied accountCode={} policy={} strategy=legacy_amount_threshold",
                        accountCode,
                        fallbackPolicy
                );
                yield evaluateByAmount(amount);
            }
        };
    }

    private FraudAnalysisResult evaluateWithProfile(BigDecimal amount, RiskProfileSnapshot profile) {
        if (profile.tier() == RiskTier.RESTRICTED || profile.tier() == RiskTier.HIGH) {
            log.info("Risk profile gate blocked accountCode={} tier={} score={}",
                    profile.accountCode(), profile.tier(), profile.dynamicScore());
            return FraudAnalysisResult.BLOCKED;
        }

        if (profile.tier() == RiskTier.MEDIUM) {
            log.info("Risk profile gate suspicious accountCode={} tier={} score={}",
                    profile.accountCode(), profile.tier(), profile.dynamicScore());
            return FraudAnalysisResult.SUSPICIOUS;
        }

        return evaluateByAmount(amount);
    }

    private FraudAnalysisResult evaluateByAmount(BigDecimal amount) {
        if (amount.compareTo(properties.blockedThreshold()) >= 0) {
            return FraudAnalysisResult.BLOCKED;
        }
        if (amount.compareTo(properties.suspiciousThreshold()) >= 0) {
            return FraudAnalysisResult.SUSPICIOUS;
        }
        return FraudAnalysisResult.CLEAR;
    }
}
