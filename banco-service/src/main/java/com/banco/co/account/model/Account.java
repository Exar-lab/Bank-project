package com.banco.co.account.model;

import com.banco.co.account.exception.account.AccountBlockedFundsException;
import com.banco.co.account.exception.account.AccountInsufficientFundsException;
import com.banco.co.account.exception.account.AccountInvalidAmountException;
import com.banco.co.account.exception.account.AccountMaxWithdrawExceededException;
import com.banco.co.security.codeGenerator.CodeGenerator;
import com.banco.co.security.cryptoLib.JasyptEncryptor;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.card.model.Card;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Account {
    /**
     * Entidad de cuenta vinculada a un usuario
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String accountCode; // Se muestra al usuario

    @Convert(converter = JasyptEncryptor.class) // Encriptado
    @Column(nullable = false, unique = true, length = 255)
    private String accountNumber; // Se muestra al usuario, pero solo al propietario

    // Relaciones
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL,orphanRemoval = true,fetch = FetchType.LAZY)
    private List<Card> cards = new ArrayList<>();  // Una cuenta puede tener VARIAS tarjetas


    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;  // Balance - retenciones

    @Column(precision = 19, scale = 2)
    private BigDecimal overdraftLimit = BigDecimal.ZERO;  // Sobregiro permitido

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;  // Tasa de interés anual (ej: 5.25%)

    // Tipo y estado
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;  // SAVINGS, CHECKING, PAYROLL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(length = 3, nullable = false)
    private String currency = "CRC";  // Código ISO 4217

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastTransactionAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal blockedBalance = BigDecimal.ZERO; // Fondos en proceso

    // Envelopes (si usas sistema de sobres/presupuestos)
    @OneToMany(mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private Set<Envelope> envelopes = new HashSet<>();


    @Column(nullable = false)
    private Integer maxEnvelope = 10;

    @Column(precision = 5, scale = 2)
    private BigDecimal moneyFromEnvelope = BigDecimal.ZERO; // Cantidad de dinero en sobres

    // Generar código antes de persistir
    @PrePersist
    public void generateAccountCode() {
        if (this.accountCode == null) {
            this.accountCode = CodeGenerator.generateWithPrefix("CR",20);
        }
        // Generar número de cuenta aleatorio si no existe
        if (this.accountNumber == null) {
            this.accountNumber = CodeGenerator.generateRandomNumeric(12);
        }
    }

    // Métodos de negocio
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountInvalidAmountException(amount, "Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    /**
     * Balance total (disponible + bloqueado)
     */
    public BigDecimal getTotalBalance() {
        return balance.add(blockedBalance);
    }

    public BigDecimal getAvailableBalance() {
        // Saldo disponible: balance total menos fondos bloqueados en proceso
        return this.balance.subtract(this.blockedBalance);
    }
    /**
     * Bloquear fondos para una transacción pendiente
     */
    public void blockFunds(BigDecimal amount) {
        BigDecimal maxWithdraw = this.getAvailableBalance().add(this.overdraftLimit);
        if (balance.compareTo(amount) < 0) {
            throw new AccountInsufficientFundsException(this.accountCode,amount,maxWithdraw);
        }

        balance = balance.subtract(amount);
        blockedBalance = blockedBalance.add(amount);
    }

    /**
     * Desbloquear fondos (si transacción falla o se cancela)
     */
    public void unblockFunds(BigDecimal amount) {
        if (blockedBalance.compareTo(amount) < 0) {
            throw new AccountBlockedFundsException(this.accountCode, amount, blockedBalance, "unblock");
        }

        blockedBalance = blockedBalance.subtract(amount);
        balance = balance.add(amount);
    }

    /**
     * Confirmar la salida definitiva de fondos bloqueados.
     * Llamar cuando una transferencia se completa: los fondos bloqueados
     * salen de la cuenta (fueron a la cuenta destino).
     */
    public void confirmBlockedFunds(BigDecimal amount) {
        if (blockedBalance.compareTo(amount) < 0) {
            throw new AccountBlockedFundsException(this.accountCode, amount, blockedBalance, "confirm");
        }
        blockedBalance = blockedBalance.subtract(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountInvalidAmountException(amount, "Amount must be positive");
        }
        BigDecimal available = this.getAvailableBalance();
        BigDecimal maxWithdraw = available.add(this.overdraftLimit);
        if (amount.compareTo(maxWithdraw) > 0) {
            throw new AccountMaxWithdrawExceededException(this.accountCode, amount, maxWithdraw);
        }
        if (amount.compareTo(available) > 0) {
            throw new AccountInsufficientFundsException(this.accountCode, amount, available);
        }
        this.balance = this.balance.subtract(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }
    public void addEnvelope(Envelope envelope) {
        this.envelopes.add(envelope);
        envelope.setAccount(this);
    }
    public void removeEnvelope(Envelope envelope) {
        this.envelopes.remove(envelope);
        envelope.setAccount(null);
    }

    public void depositFromEnvelope(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.moneyFromEnvelope = this.moneyFromEnvelope.subtract(amount);
    }

    public void withdrawFromEnvelope(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.moneyFromEnvelope = this.moneyFromEnvelope.add(amount);
    }

}
