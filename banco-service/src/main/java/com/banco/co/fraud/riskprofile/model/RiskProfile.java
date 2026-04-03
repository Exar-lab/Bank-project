package com.banco.co.fraud.riskprofile.model;

import com.banco.co.fraud.riskprofile.enums.RiskTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_risk_profiles_account_code", columnNames = "account_code")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_code", nullable = false, length = 64)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier", nullable = false, length = 20)
    private RiskTier riskTier;

    @Column(name = "dynamic_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal dynamicScore;

    @Column(name = "profile_version", nullable = false)
    private Long profileVersion;

    @Column(name = "rule_set_version", nullable = false, length = 32)
    private String ruleSetVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
