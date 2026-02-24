package com.banco.co.card.model;

import com.banco.co.Transaction.enums.TransactionType;
import com.banco.co.account.model.Account;
import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import com.banco.co.card.exception.card.CardExpiredException;
import com.banco.co.card.exception.card.CardNotActiveException;
import com.banco.co.card.generator.CardNumberGenerator;
import com.banco.co.security.codeGenerator.CodeGenerator;
import com.banco.co.security.cryptoLib.JasyptEncryptor;
import com.banco.co.security.securityhasher.HashUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador visible para el usuario
    @Column(unique = true, nullable = false, length = 20)
    private String cardCode;  // Ej: "CARD-2024-X7K9P2"

    // Número de tarjeta ENCRIPTADO (16 dígitos)
    @Convert(converter = JasyptEncryptor.class)
    @Column(nullable = false, unique = true, length = 255)
    private String cardNumber;

    // CVV/CVC ENCRIPTADO  SÍ debe estar encriptado
    @Convert(converter = JasyptEncryptor.class)
    @Column(nullable = false, length = 255)
    private String securityCode;  // CVV: 3-4 dígitos

    // PIN ENCRIPTADO con hash (Bcrypt, no reversible)
    @Column(nullable = false, length = 60)
    private String pinHash;

    // Tipo de tarjeta
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType cardType;  // DEBIT, CREDIT, PREPAID

    @Enumerated(EnumType.STRING)
    private CardBrand brand;  // VISA, MASTERCARD, AMEX

    @Enumerated(EnumType.STRING)
    private CardTier tier;  // CLASSIC, GOLD, PLATINUM, BLACK

    // Relación con cuenta - SIN CASCADE
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Estado
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status = CardStatus.INACTIVE;  // Debe activarse manualmente

    @Column(length = 500)
    private String blockedReason;  // Si está bloqueada, ¿por qué?

    // Fechas
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expirationDate;  // 6 años después

    @Column
    private LocalDateTime activatedAt;  // Cuando el usuario la activa

    @Column
    private LocalDateTime lastUsedAt;  // Última vez que se usó

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Límites de gasto
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailySpent = BigDecimal.ZERO;  // Resetear diariamente

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlySpent = BigDecimal.ZERO;  // Resetear mensualmente

    // Características
    @Column(nullable = false)
    private boolean contactlessEnabled = true;

    @Column(nullable = false)
    private boolean onlinePaymentsEnabled = true;

    @Column(nullable = false)
    private boolean internationalEnabled = false;

    // Puntos (si manejas acumulación por tarjeta)
    @Column(nullable = false)
    private Long points = 0L;

    // Última transacción (información básica)
    @Column
    private LocalDateTime lastTransactionAt;

    @Column(precision = 19, scale = 2)
    private BigDecimal lastTransactionAmount;

    @Enumerated(EnumType.STRING)
    private TransactionType lastTransactionType;

    // Generación automática de datos
    @PrePersist
    public void generateCardData() {
        // Generar código visible
        if (this.cardCode == null) {
            this.cardCode = CodeGenerator.generateWithChars(6,"CARD");
        }

        // Generar número de tarjeta válido (con Luhn)
        if (this.cardNumber == null) {
            this.cardNumber = CardNumberGenerator.generateValid(this.brand);
        }

        // Generar CVV según tipo de tarjeta
        if (this.securityCode == null) {
            int cvvLength = (this.brand == CardBrand.AMEX) ? 4 : 3;
            this.securityCode = CodeGenerator.generateRandomNumeric(cvvLength);
        }

        // Fecha de expiración: 6 años
        if (this.expirationDate == null) {
            this.expirationDate = LocalDateTime.now().plusYears(6);
        }

        // Límites por defecto según tier
        if (this.dailyLimit == null) {
            this.dailyLimit = getDefaultDailyLimit();
        }
        if (this.monthlyLimit == null) {
            this.monthlyLimit = getDefaultMonthlyLimit();
        }
    }

    // Métodos de negocio
    public void activate(String pin) {
        if (this.status != CardStatus.INACTIVE) throw new CardNotActiveException(this.cardCode,this.status.toString().toLowerCase());

        if(isExpired()) throw new CardExpiredException(this.cardCode,this.status.toString().toLowerCase());

        this.pinHash = HashUtils.hash(pin);
        this.status = CardStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void block(String reason) {
        this.status = CardStatus.BLOCKED;
        this.blockedReason = reason;
    }

    public void reportStolen() {
        this.status = CardStatus.STOLEN;
        this.blockedReason = "Reported as stolen by cardholder";
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expirationDate);
    }


    public boolean canTransact(BigDecimal amount) {
        if (this.status != CardStatus.ACTIVE) return false;
        if (isExpired()) return false;

        BigDecimal newDailyTotal = this.dailySpent.add(amount);
        BigDecimal newMonthlyTotal = this.monthlySpent.add(amount);

        return newDailyTotal.compareTo(this.dailyLimit) <= 0
                && newMonthlyTotal.compareTo(this.monthlyLimit) <= 0;
    }

    public void recordTransaction(BigDecimal amount, TransactionType type) {
        this.lastTransactionAt = LocalDateTime.now();
        this.lastTransactionAmount = amount;
        this.lastTransactionType = type;
        this.lastUsedAt = LocalDateTime.now();

        this.dailySpent = this.dailySpent.add(amount);
        this.monthlySpent = this.monthlySpent.add(amount);
    }

    public void resetDailySpent() {
        this.dailySpent = BigDecimal.ZERO;
    }

    public void resetMonthlySpent() {
        this.monthlySpent = BigDecimal.ZERO;
    }

    private BigDecimal getDefaultDailyLimit() {
        return switch (this.tier) {
            case CLASSIC -> new BigDecimal("500000");  // Está en colones
            case GOLD -> new BigDecimal("1000000");
            case PLATINUM -> new BigDecimal("5000000");
            case BLACK -> new BigDecimal("10000000");
        };
    }

    private BigDecimal getDefaultMonthlyLimit() {
        return getDefaultDailyLimit().multiply(new BigDecimal("30"));
    }
}
