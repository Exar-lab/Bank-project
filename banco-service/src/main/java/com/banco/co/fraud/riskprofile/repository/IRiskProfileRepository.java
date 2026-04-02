package com.banco.co.fraud.riskprofile.repository;

import com.banco.co.fraud.riskprofile.model.RiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IRiskProfileRepository extends JpaRepository<RiskProfile, UUID> {

    @Transactional(readOnly = true)
    Optional<RiskProfile> findByAccountCode(String accountCode);
}
