package com.banco.co.fraud.riskprofile.repository;

import com.banco.co.fraud.riskprofile.model.RiskProfileEventProcessing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface IRiskProfileEventProcessingRepository extends JpaRepository<RiskProfileEventProcessing, Long> {

    @Transactional(readOnly = true)
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
