package com.banco.co.fraud.riskprofile.adapter;

import com.banco.co.fraud.riskprofile.dto.RiskProfileSnapshot;
import com.banco.co.fraud.riskprofile.dto.RiskProfileUpdateCommand;
import com.banco.co.fraud.riskprofile.enums.RiskProfileEventStatus;
import com.banco.co.fraud.riskprofile.enums.RiskTier;
import com.banco.co.fraud.riskprofile.model.RiskProfile;
import com.banco.co.fraud.riskprofile.model.RiskProfileEventProcessing;
import com.banco.co.fraud.riskprofile.port.IRiskProfilePort;
import com.banco.co.fraud.riskprofile.port.IRiskProfileQueryPort;
import com.banco.co.fraud.riskprofile.port.IRiskProfileUpdatePort;
import com.banco.co.fraud.riskprofile.repository.IRiskProfileEventProcessingRepository;
import com.banco.co.fraud.riskprofile.repository.IRiskProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class RiskProfileJpaAdapter implements IRiskProfilePort, IRiskProfileQueryPort, IRiskProfileUpdatePort {

    private static final String DEFAULT_RULE_SET_VERSION = "v1";

    private final IRiskProfileRepository riskProfileRepository;
    private final IRiskProfileEventProcessingRepository riskProfileEventProcessingRepository;

    public RiskProfileJpaAdapter(
            IRiskProfileRepository riskProfileRepository,
            IRiskProfileEventProcessingRepository riskProfileEventProcessingRepository
    ) {
        this.riskProfileRepository = riskProfileRepository;
        this.riskProfileEventProcessingRepository = riskProfileEventProcessingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskProfileSnapshot> findByAccountCode(String accountCode) {
        return riskProfileRepository.findByAccountCode(accountCode)
                .map(this::toSnapshot);
    }

    @Override
    @Transactional
    public void upsertFromEvent(RiskProfileUpdateCommand command) {
        RiskProfile profile = riskProfileRepository.findByAccountCode(command.accountCode())
                .orElseGet(() -> newRiskProfile(command.accountCode()));

        profile.setRiskTier(defaultTierIfNull(command.riskTier()));
        profile.setDynamicScore(defaultScoreIfNull(command.dynamicScore()));
        profile.setRuleSetVersion(command.ruleSetVersion() == null ? DEFAULT_RULE_SET_VERSION : command.ruleSetVersion());
        profile.setProfileVersion(profile.getProfileVersion() + 1);

        riskProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public boolean markEventProcessed(String eventId, String consumerName) {
        try {
            riskProfileEventProcessingRepository.saveAndFlush(
                    new RiskProfileEventProcessing(
                            eventId,
                            consumerName,
                            LocalDateTime.now(ZoneOffset.UTC),
                            RiskProfileEventStatus.PROCESSED
                    )
            );
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private RiskProfileSnapshot toSnapshot(RiskProfile profile) {
        return new RiskProfileSnapshot(
                profile.getAccountCode(),
                profile.getRiskTier(),
                profile.getDynamicScore(),
                profile.getProfileVersion(),
                profile.getRuleSetVersion(),
                profile.getUpdatedAt() == null ? null : profile.getUpdatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private RiskProfile newRiskProfile(String accountCode) {
        RiskProfile profile = new RiskProfile();
        profile.setAccountCode(accountCode);
        profile.setRiskTier(RiskTier.LOW);
        profile.setDynamicScore(BigDecimal.ZERO);
        profile.setProfileVersion(0L);
        profile.setRuleSetVersion(DEFAULT_RULE_SET_VERSION);
        return profile;
    }

    private RiskTier defaultTierIfNull(RiskTier riskTier) {
        return riskTier == null ? RiskTier.LOW : riskTier;
    }

    private BigDecimal defaultScoreIfNull(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }
}
