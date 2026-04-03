package com.banco.co.fraud.riskprofile.port;

import com.banco.co.fraud.riskprofile.dto.RiskProfileSnapshot;
import com.banco.co.fraud.riskprofile.dto.RiskProfileUpdateCommand;

import java.util.Optional;

public interface IRiskProfilePort {

    Optional<RiskProfileSnapshot> findByAccountCode(String accountCode);

    void upsertFromEvent(RiskProfileUpdateCommand command);

    boolean markEventProcessed(String eventId, String consumerName);
}
