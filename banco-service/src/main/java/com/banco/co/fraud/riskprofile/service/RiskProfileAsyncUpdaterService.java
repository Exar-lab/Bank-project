package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.riskprofile.config.RiskProfileAsyncUpdaterProperties;
import com.banco.co.fraud.riskprofile.dto.RiskProfileUpdateCommand;
import com.banco.co.fraud.riskprofile.dto.TransactionCompletedRiskEvent;
import com.banco.co.fraud.riskprofile.enums.RiskTier;
import com.banco.co.fraud.riskprofile.port.IRiskProfileUpdatePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RiskProfileAsyncUpdaterService {

    private final IRiskProfileUpdatePort riskProfileUpdatePort;
    private final RiskProfileAsyncUpdaterProperties properties;

    public RiskProfileAsyncUpdaterService(
            IRiskProfileUpdatePort riskProfileUpdatePort,
            RiskProfileAsyncUpdaterProperties properties
    ) {
        this.riskProfileUpdatePort = riskProfileUpdatePort;
        this.properties = properties;
    }

    @Transactional
    public boolean updateFromTransactionCompleted(TransactionCompletedRiskEvent event) {
        if (!riskProfileUpdatePort.markEventProcessed(event.eventId(), properties.consumerName())) {
            return false;
        }

        RiskTier riskTier = resolveRiskTier(event.amount());
        BigDecimal score = resolveDynamicScore(riskTier);

        riskProfileUpdatePort.upsertFromEvent(new RiskProfileUpdateCommand(
                event.eventId(),
                event.accountCode(),
                riskTier,
                score,
                properties.ruleSetVersion()
        ));
        return true;
    }

    private RiskTier resolveRiskTier(BigDecimal amount) {
        if (amount.compareTo(properties.restrictedThreshold()) >= 0) {
            return RiskTier.RESTRICTED;
        }
        if (amount.compareTo(properties.highThreshold()) >= 0) {
            return RiskTier.HIGH;
        }
        if (amount.compareTo(properties.mediumThreshold()) >= 0) {
            return RiskTier.MEDIUM;
        }
        return RiskTier.LOW;
    }

    private BigDecimal resolveDynamicScore(RiskTier riskTier) {
        return switch (riskTier) {
            case LOW -> properties.lowScore();
            case MEDIUM -> properties.mediumScore();
            case HIGH -> properties.highScore();
            case RESTRICTED -> properties.restrictedScore();
        };
    }
}
