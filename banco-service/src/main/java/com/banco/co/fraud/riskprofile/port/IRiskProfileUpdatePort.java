package com.banco.co.fraud.riskprofile.port;

import com.banco.co.fraud.riskprofile.dto.RiskProfileUpdateCommand;

public interface IRiskProfileUpdatePort {

    void upsertFromEvent(RiskProfileUpdateCommand command);

    boolean markEventProcessed(String eventId, String consumerName);
}
