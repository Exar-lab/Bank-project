package com.banco.co.fraud.riskprofile.dto;

import com.banco.co.fraud.riskprofile.enums.RiskTier;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskProfileSnapshot(
        String accountCode,
        RiskTier tier,
        BigDecimal dynamicScore,
        Long profileVersion,
        String ruleSetVersion,
        Instant lastEvaluatedAt
) {
}
