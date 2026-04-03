package com.banco.co.fraud.config;

import com.banco.co.fraud.riskprofile.enums.RiskProfileFallbackPolicy;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "fraud.detection")
public record FraudDetectionProperties(
    boolean riskProfileEnabled,
    BigDecimal suspiciousThreshold,
    BigDecimal blockedThreshold,
    @DefaultValue("25") long riskProfileQueryTimeoutMs,
    @DefaultValue("FAIL_OPEN_LEGACY_THRESHOLD") RiskProfileFallbackPolicy riskProfileFallbackPolicy
) {}
