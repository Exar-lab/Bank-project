package com.banco.co.fraud.riskprofile.port;

import com.banco.co.fraud.riskprofile.dto.RiskProfileSnapshot;

import java.util.Optional;

public interface IRiskProfileQueryPort {

    Optional<RiskProfileSnapshot> findByAccountCode(String accountCode);
}
