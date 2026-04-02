package com.banco.co.fraud.riskprofile.model;

import com.banco.co.fraud.riskprofile.enums.RiskProfileEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_profile_event_processing",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_risk_profile_event_consumer", columnNames = {"event_id", "consumer_name"})
        },
        indexes = {
                @Index(name = "idx_risk_profile_event_processed", columnList = "processed_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class RiskProfileEventProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private RiskProfileEventStatus processingStatus;

    public RiskProfileEventProcessing(String eventId,
                                      String consumerName,
                                      LocalDateTime processedAt,
                                      RiskProfileEventStatus processingStatus) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
        this.processingStatus = processingStatus;
    }
}
