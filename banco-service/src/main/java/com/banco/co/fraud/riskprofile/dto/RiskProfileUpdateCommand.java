package com.banco.co.fraud.riskprofile.dto;

import com.banco.co.fraud.riskprofile.enums.RiskTier;

import java.math.BigDecimal;

public record RiskProfileUpdateCommand(
        String eventId,
        String accountCode,
        RiskTier riskTier,
        BigDecimal dynamicScore,
        String ruleSetVersion
) {
}
