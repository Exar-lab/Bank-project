package com.banco.co.envelope.model;

import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.account.model.Account;
import com.banco.co.envelope.enums.AutoContributeFrequency;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.exception.EnvelopeInsufficientFundsException;
import com.banco.co.envelope.exception.EnvelopeLockedException;
import com.banco.co.envelope.generator.EnvelopeCodeGenerator;
import com.banco.co.transaction.exception.transaction.TransactionInvalidAmountException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
@Table(name = "envelopes", indexes = {
        @Index(name = "idx_account_id", columnList = "account_id"),
        @Index(name = "idx_envelope_code", columnList = "envelopeCode")
})
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Envelope {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador visible
    @Column(unique = true, nullable = false, length = 20)
    private String envelopeCode;  // ENV-2024-X7K9P2

    // Información básica
    @Column(nullable = false, length = 100)
    private String name;  // "Vacaciones 2026", "Fondo de emergencia"

    @Column(length = 500,nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvelopeType type;  // SAVINGS, INVESTMENT, VACATION, EMERGENCY, GOAL, OTHER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvelopeStatus status = EnvelopeStatus.ACTIVE;

    // Relación con cuenta - SIN CASCADE
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Balance actual
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumBalance = BigDecimal.ZERO;  // No puede bajar de esto

    // Meta/Objetivo
    @Column(precision = 19, scale = 2)
    private BigDecimal targetAmount;  // Meta a alcanzar

    @Column
    private LocalDate targetDate;  // Fecha objetivo

    @Column(precision = 5, scale = 2)
    private BigDecimal progressPercentage = BigDecimal.ZERO;  // % completado

    // Contribuciones automáticas
    @Column(nullable = false)
    private boolean autoContribute = false;

    @Column(precision = 19, scale = 2)
    private BigDecimal autoContributeAmount;

    @Enumerated(EnumType.STRING)
    private AutoContributeFrequency autoContributeFrequency;  // DAILY, WEEKLY, MONTHLY

    @Column
    private LocalDate nextContributionDate;

    @Column
    private LocalDate lastContributionDate;

    // Reglas de redondeo (Round-up rules)
    @Column(nullable = false)
    private boolean roundUpEnabled = false;

    @Column(precision = 19, scale = 2)
    private BigDecimal roundUpMultiple = new BigDecimal("1.00");  // Redondear al peso/dólar más cercano

    // Bloqueos
    @Column(nullable = false)
    private boolean locked = false;

    @Column
    private LocalDate lockedUntil;

    @Column(length = 200)
    private String lockReason;

    // Personalización visual
    @Column(length = 10)
    private String icon;  // Emoji: 🏖️, 🏠, 🎓, 🚗, 💰

    @Column(length = 7)
    private String color;  // Hex: #FF5733

    @Column
    private Integer priority = 0;  // Para ordenar múltiples sobres

    // Última transacción
    @Column
    private LocalDateTime lastTransactionAt;

    @Column(precision = 19, scale = 2)
    private BigDecimal lastTransactionAmount;

    @Enumerated(EnumType.STRING)
    private TransactionType lastTransactionType;  // DEPOSIT, WITHDRAWAL, TRANSFER

    // Estadísticas
    @Column(nullable = false)
    private Long totalDeposits = 0L;

    @Column(nullable = false)
    private Long totalWithdrawals = 0L;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDepositedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalWithdrawnAmount = BigDecimal.ZERO;

    // Auditoría
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime completedAt;  // Cuando se alcanzó la meta

    // Generación automática
    @PrePersist
    public void generateEnvelopeData() {
        if (this.envelopeCode == null) {
            this.envelopeCode = EnvelopeCodeGenerator.generate();
        }
        if (this.icon == null) {
            this.icon = getDefaultIcon();
        }
        if (this.color == null) {
            this.color = getDefaultColor();
        }
        if (targetAmount != null && targetAmount.compareTo(BigDecimal.ZERO) > 0) {
            updateProgress();
        }


    }
    public boolean getAutoContribute() {
        return autoContribute;
    }



    // Métodos de negocio
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionInvalidAmountException(amount,"Amount must be greater than zero");
        }

        this.balance = this.balance.add(amount);
        this.totalDeposits++;
        this.totalDepositedAmount = this.totalDepositedAmount.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
        this.lastTransactionAmount = amount;
        this.lastTransactionType = TransactionType.DEPOSIT;

        updateProgress();
        checkGoalCompletion();
    }

    public void withdraw(BigDecimal amount) {
        if (this.locked) {
            throw new EnvelopeLockedException(this.envelopeCode,this.getLockedUntil(),this.lockReason);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionInvalidAmountException(amount,"Amount must be greater than zero");
        }


        BigDecimal newBalance = this.balance.subtract(amount);
        if (newBalance.compareTo(this.minimumBalance) < 0) {
            throw new EnvelopeInsufficientFundsException(amount,
                    this.balance.subtract(this.minimumBalance),
                    this.envelopeCode
            );
        }

        this.balance = newBalance;
        this.totalWithdrawals++;
        this.totalWithdrawnAmount = this.totalWithdrawnAmount.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
        this.lastTransactionAmount = amount;
        this.lastTransactionType = TransactionType.WITHDRAWAL;

        updateProgress();
    }

    public void updateProgress() {
        if (this.targetAmount != null && this.targetAmount.compareTo(BigDecimal.ZERO) > 0 && this.balance != null) {
            this.progressPercentage = this.balance
                    .divide(this.targetAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    public void checkGoalCompletion() {
        if (this.targetAmount != null
                && this.balance.compareTo(this.targetAmount) >= 0
                && this.completedAt == null) {
            this.completedAt = LocalDateTime.now();
            this.status = EnvelopeStatus.COMPLETED;
        }
    }

    public boolean isGoalCompleted() {
        return this.completedAt != null;
    }

    public boolean isLocked() {
        if (!this.locked) return false;
        if (this.lockedUntil == null) return true;
        return LocalDate.now().isBefore(this.lockedUntil);
    }

    public void lock(LocalDate until, String reason) {
        if (isLocked()) {
            throw new EnvelopeLockedException(this.envelopeCode,this.lockedUntil,this.lockReason);
        }
        this.locked = true;
        this.lockedUntil = until;
        this.lockReason = reason;
    }

    public void unlock() {
        this.locked = false;
        this.lockedUntil = null;
        this.lockReason = null;
    }

    public BigDecimal getAvailableBalance() {
        return this.balance.subtract(this.minimumBalance);
    }

    public BigDecimal getRemainingToGoal() {
        if (this.targetAmount == null) return BigDecimal.ZERO;
        BigDecimal remaining = this.targetAmount.subtract(this.balance);
        return remaining.max(BigDecimal.ZERO);
    }

    public long getDaysUntilTarget() {
        if (this.targetDate == null) return -1;
        return ChronoUnit.DAYS.between(LocalDate.now(), this.targetDate);
    }

    public BigDecimal getMonthlyContributionNeeded() {
        if (this.targetDate == null || this.targetAmount == null) {
            return BigDecimal.ZERO;
        }

        long daysRemaining = getDaysUntilTarget();
        if (daysRemaining <= 0) return BigDecimal.ZERO;

        BigDecimal remaining = getRemainingToGoal();
        long monthsRemaining = daysRemaining / 30;

        if (monthsRemaining == 0) return remaining;

        return remaining.divide(
                new BigDecimal(monthsRemaining),
                2,
                RoundingMode.UP
        );
    }
    public boolean hasReachedGoal() {
        return Objects.equals(this.progressPercentage, BigDecimal.valueOf(100));
    }

    private String getDefaultIcon() {
        return switch (this.type) {
            case VACATION -> "🏖️";
            case EMERGENCY -> "🚨";
            case INVESTMENT -> "📈";
            case SAVINGS -> "💰";
            case EDUCATION -> "🎓";
            case HOME -> "🏠";
            case CAR -> "🚗";
            default -> "📦";
        };
    }

    private String getDefaultColor() {
        return switch (this.type) {
            case VACATION -> "#00D4FF";
            case EMERGENCY -> "#FF5733";
            case INVESTMENT -> "#4CAF50";
            case SAVINGS -> "#FFC107";
            case EDUCATION -> "#9C27B0";
            case HOME -> "#FF9800";
            case CAR -> "#2196F3";
            default -> "#607D8B";
        };
    }
}
