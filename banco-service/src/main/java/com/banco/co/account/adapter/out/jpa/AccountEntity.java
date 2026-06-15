package com.banco.co.account.adapter.out.jpa;

import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.card.model.Card;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.security.cryptoLib.JasyptEncryptor;
import com.banco.co.user.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Anemic JPA entity — only JPA annotations + getters/setters, ZERO business logic.
 * Uses the same table as the legacy com.banco.co.account.model.Account entity.
 * Cross-feature JPA relationships (User, Card, Envelope) are kept for DB integrity.
 * The domain model uses IDs only — the adapter layer translates between them.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "account")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String accountCode;

    @Convert(converter = JasyptEncryptor.class)
    @Column(nullable = false, unique = true, length = 255)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Card> cards = new ArrayList<>();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal overdraftLimit = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(length = 3, nullable = false)
    private String currency = "CRC";

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastTransactionAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal blockedBalance = BigDecimal.ZERO;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Envelope> envelopes = new HashSet<>();

    @Column(nullable = false)
    private Integer maxEnvelope = 10;

    @Column(precision = 5, scale = 2)
    private BigDecimal moneyFromEnvelope = BigDecimal.ZERO;

    // ══════════════════════════════════════════════════════════
    // Getters and Setters — no Lombok @Data on @Entity
    // ══════════════════════════════════════════════════════════

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(BigDecimal overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastTransactionAt() {
        return lastTransactionAt;
    }

    public void setLastTransactionAt(LocalDateTime lastTransactionAt) {
        this.lastTransactionAt = lastTransactionAt;
    }

    public BigDecimal getBlockedBalance() {
        return blockedBalance;
    }

    public void setBlockedBalance(BigDecimal blockedBalance) {
        this.blockedBalance = blockedBalance;
    }

    public Set<Envelope> getEnvelopes() {
        return envelopes;
    }

    public void setEnvelopes(Set<Envelope> envelopes) {
        this.envelopes = envelopes;
    }

    public Integer getMaxEnvelope() {
        return maxEnvelope;
    }

    public void setMaxEnvelope(Integer maxEnvelope) {
        this.maxEnvelope = maxEnvelope;
    }

    public BigDecimal getMoneyFromEnvelope() {
        return moneyFromEnvelope;
    }

    public void setMoneyFromEnvelope(BigDecimal moneyFromEnvelope) {
        this.moneyFromEnvelope = moneyFromEnvelope;
    }

    // ══════════════════════════════════════════════════════════
    // Business methods — mirrors legacy Account for EnvelopeService + EnvelopeScheduleService
    // ══════════════════════════════════════════════════════════

    /**
     * Returns the available balance (full balance — no blocked funds tracked at entity level).
     * Mirrors com.banco.co.account.model.Account#getAvailableBalance().
     */
    public BigDecimal getAvailableBalance() {
        return this.balance;
    }

    /**
     * Withdraws an amount from the account balance.
     * Used by EnvelopeScheduleService auto-contribution flow.
     */
    public void withdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * Returns funds from an envelope back to the account balance.
     * Used by EnvelopeService.deposit() (envelope → account return flow).
     */
    public void depositFromEnvelope(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.moneyFromEnvelope = this.moneyFromEnvelope.subtract(amount);
    }

    /**
     * Moves funds from account balance to an envelope.
     * Used by EnvelopeService.withdraw() (account → envelope allocation flow).
     */
    public void withdrawFromEnvelope(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.moneyFromEnvelope = this.moneyFromEnvelope.add(amount);
    }
}
