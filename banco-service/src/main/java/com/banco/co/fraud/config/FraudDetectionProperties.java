package com.banco.co.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "fraud.detection")
public record FraudDetectionProperties(
    BigDecimal suspiciousThreshold,
    BigDecimal blockedThreshold
) {}
