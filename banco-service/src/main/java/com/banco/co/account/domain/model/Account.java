package com.banco.co.account.domain.model;

import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.exception.account.AccountBlockedFundsException;
import com.banco.co.account.exception.account.AccountInsufficientFundsException;
import com.banco.co.account.exception.account.AccountInvalidAmountException;
import com.banco.co.account.exception.account.AccountMaxWithdrawExceededException;
import com.banco.co.account.generator.AccountCodeGenerator;
import com.banco.co.account.generator.AccountNumberGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain model — ZERO JPA, ZERO Spring imports.
 * Cross-feature references use UUID only:
 *   - userId instead of User entity
 *   - cardIds instead of List<Card>
 *   - envelopeIds instead of Set<Envelope>
 * Coexists with com.banco.co.account.model.Account during the additive migration phase.
 */
public class Account {

    private UUID id;

    private String accountCode;

    private String accountNumber;

    // Cross-feature reference: replaced @ManyToOne User with UUID
    private UUID userId;

    // Cross-feature reference: replaced @OneToMany Card with List<UUID>
    private List<UUID> cardIds = new ArrayList<>();

    private BigDecimal balance = BigDecimal.ZERO;

    private BigDecimal overdraftLimit = BigDecimal.ZERO;

    private BigDecimal interestRate;

    private AccountType accountType;

    private AccountStatus status = AccountStatus.ACTIVE;

    private String currency = "CRC";

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastTransactionAt;

    private BigDecimal blockedBalance = BigDecimal.ZERO;

    // Cross-feature reference: replaced Set<Envelope> with List<UUID>
    private List<UUID> envelopeIds = new ArrayList<>();

    private Integer maxEnvelope = 10;

    private BigDecimal moneyFromEnvelope = BigDecimal.ZERO;

    public Account() {
    }

    // ══════════════════════════════════════════════════════════
    // Domain lifecycle — mirrors @PrePersist logic from legacy entity
    // ══════════════════════════════════════════════════════════

    public void generateAccountCode() {
        if (this.accountCode == null) {
            this.accountCode = AccountCodeGenerator.generate();
        }
        if (this.accountNumber == null) {
            this.accountNumber = AccountNumberGenerator.generate();
        }
    }

    // ══════════════════════════════════════════════════════════
    // Business methods
    // ══════════════════════════════════════════════════════════

    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountInvalidAmountException(amount, "Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    public BigDecimal getTotalBalance() {
        return balance.add(blockedBalance);
    }

    public BigDecimal getAvailableBalance() {
        return this.balance;
    }

    public void blockFunds(BigDecimal amount) {
        BigDecimal maxWithdraw = this.balance.add(this.overdraftLimit);
        if (amount.compareTo(maxWithdraw) > 0) {
            throw new AccountInsufficientFundsException(this.accountCode, amount, maxWithdraw);
        }
        balance = balance.subtract(amount);
        blockedBalance = blockedBalance.add(amount);
    }

    public void unblockFunds(BigDecimal amount) {
        if (blockedBalance.compareTo(amount) < 0) {
            throw new AccountBlockedFundsException(this.accountCode, amount, this.blockedBalance, "unblock");
        }
        blockedBalance = blockedBalance.subtract(amount);
        balance = balance.add(amount);
    }

    public void confirmBlockedFunds(BigDecimal amount) {
        if (blockedBalance.compareTo(amount) < 0) {
            throw new AccountBlockedFundsException(this.accountCode, amount, this.blockedBalance, "confirm");
        }
        blockedBalance = blockedBalance.subtract(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountInvalidAmountException(amount, "Amount must be positive");
        }
        BigDecimal maxWithdraw = this.getAvailableBalance().add(this.overdraftLimit);
        if (amount.compareTo(maxWithdraw) > 0) {
            throw new AccountMaxWithdrawExceededException(this.accountCode, amount, maxWithdraw);
        }
        this.balance = this.balance.subtract(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    public void addEnvelopeId(UUID envelopeId) {
        this.envelopeIds.add(envelopeId);
    }

    public void depositFromEnvelope(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.moneyFromEnvelope = this.moneyFromEnvelope.subtract(amount);
    }

    public void withdrawFromEnvelope(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.moneyFromEnvelope = this.moneyFromEnvelope.add(amount);
    }

    // ══════════════════════════════════════════════════════════
    // Getters and Setters — no Lombok @Data, no JPA
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<UUID> getCardIds() {
        return cardIds;
    }

    public void setCardIds(List<UUID> cardIds) {
        this.cardIds = cardIds;
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

    public List<UUID> getEnvelopeIds() {
        return envelopeIds;
    }

    public void setEnvelopeIds(List<UUID> envelopeIds) {
        this.envelopeIds = envelopeIds;
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
}
