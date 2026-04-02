package com.banco.co.fraud.riskprofile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "fraud.detection.risk-profile-async")
public record RiskProfileAsyncUpdaterProperties(
        String consumerName,
        BigDecimal mediumThreshold,
        BigDecimal highThreshold,
        BigDecimal restrictedThreshold,
        BigDecimal lowScore,
        BigDecimal mediumScore,
        BigDecimal highScore,
        BigDecimal restrictedScore,
        String ruleSetVersion
) {
}
