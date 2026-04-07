package com.banco.co.transaction.model;

import com.banco.co.transaction.enums.*;
import com.banco.co.transaction.exception.transaction.TransactionStatusException;
import com.banco.co.transaction.generator.TransactionCodeGenerator;
import com.banco.co.transaction.resolver.MccCategoryResolver;
import com.banco.co.account.model.Account;
import com.banco.co.card.model.Card;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.security.securityhasher.HashUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transaction_code", columnList = "transactionCode"),
        @Index(name = "idx_from_account", columnList = "from_account_id"),
        @Index(name = "idx_to_account", columnList = "to_account_id"),
        @Index(name = "idx_created_at", columnList = "createdAt"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_idempotency_key", columnList = "idempotencyKey")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador único visible
    @Column(unique = true, nullable = false, length = 30)
    private String transactionCode;  // TXN-BCR-2024-X7K9P2M3

    // Idempotencia (evitar duplicados)
    @Column(unique = true, length = 100)
    private String idempotencyKey;  // Hash único para evitar duplicados

    @Column(length = 100)
    private String externalReference;  // Referencia de sistema externo

    // Tipo y estado
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;  // DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private TransactionChannel channel;  // WEB, MOBILE, ATM, BRANCH, POS

    // Cuentas involucradas
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;  // Cuenta de origen (puede ser null en depósitos)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;  // Cuenta de destino (puede ser null en retiros)

    // Tarjeta (si aplica)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;  // Tarjeta usada (si aplica)

    @Column(length = 4)
    private String cardLastFourDigits;  // Últimos 4 dígitos de la tarjeta

    @Column(length = 20)
    private String authorizationCode;  // Código de autorización

    // Montos
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;  // Monto de la transacción

    @Column(precision = 19, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;  // Comisión

    @Enumerated(EnumType.STRING)
    private FeeType feeType;  // PERCENTAGE, FIXED, NONE

    @Column(precision = 19, scale = 2)
    private BigDecimal netAmount;  // Monto neto (amount - fee)

    @Column(length = 3, nullable = false)
    private String currency = "CRC";  // Moneda

    @Column(precision = 10, scale = 4)
    private BigDecimal exchangeRate;  // Si es conversión de moneda

    // Balances antes/después (para auditoría)
    @Column(precision = 19, scale = 2)
    private BigDecimal fromAccountBalanceBefore;

    @Column(precision = 19, scale = 2)
    private BigDecimal fromAccountBalanceAfter;

    @Column(precision = 19, scale = 2)
    private BigDecimal toAccountBalanceBefore;

    @Column(precision = 19, scale = 2)
    private BigDecimal toAccountBalanceAfter;

    // Descripción
    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 1000)
    private String notes;  // Notas adicionales

    // Comercio/Merchant (para compras)
    @Column(length = 200)
    private String merchantName;  // Nombre del comercio

    @Column(length = 100)
    private String merchantCity;

    @Column(length = 100)
    private String merchantCountry;

    @Column(length = 10)
    private String merchantMccCode;  // Código MCC (Merchant Category Code)

    @Column(length = 100)
    private String merchantCategory;  // RESTAURANT, GAS_STATION, PHARMACY, etc.

    // Categorización para análisis
    @Enumerated(EnumType.STRING)
    private TransactionCategory category;  // FOOD, TRANSPORT, ENTERTAINMENT, etc.

    @Enumerated(EnumType.STRING)
    private TransactionSubcategory subcategory;

    @Column(length = 500)
    private String tags;  // Etiquetas separadas por comas

    // Ubicación y dispositivo
    @Column(length = 50)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String deviceId;

    @Column(length = 100)
    private String deviceType;  // ANDROID, IOS, WEB, ATM

    @Column(length = 100)
    private String locationCity;

    @Column(length = 100)
    private String locationCountry;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    // Fechas importantes
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // Cuando se inició

    @Column
    private LocalDateTime processedAt;  // Cuando se procesó

    @Column
    private LocalDateTime completedAt;  // Cuando se completó

    @Column
    private LocalDateTime scheduledFor;  // Para transacciones programadas

    // Reversión/Cancelación
    @Column
    private LocalDateTime reversedAt;

    @Column(length = 500)
    private String reversalReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id")
    private Transaction originalTransaction;  // Si es reversión, apunta a la original

    @Column
    private boolean isReversal = false;

    // Detección de fraude
    @Column(nullable = false)
    private boolean flaggedForFraud = false;

    @Column(precision = 5, scale = 2)
    private BigDecimal fraudScore;  // Score de riesgo (0-100)

    @Column(length = 500)
    private String fraudReason;

    // Aprobaciones
    @Column(length = 100)
    private String approvedBy;  // Username de quien aprobó

    @Column
    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String rejectionReason;

    // Metadata adicional (JSON flexible)
    @Column(columnDefinition = "TEXT")
    private String metadata;  // JSON con datos extra

    // Relación con sobre (si aplica)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envelope_id")
    private Envelope envelope;

    // Generación automática
    @PrePersist
    public void generateTransactionData() {
        if (this.transactionCode == null) {
            this.transactionCode = TransactionCodeGenerator.generate();
        }

        // Generar idempotency key si no existe
        if (this.idempotencyKey == null) {
            this.idempotencyKey = generateIdempotencyKey();
        }

        // Calcular monto neto
        if (this.netAmount == null) {
            this.netAmount = this.amount.subtract(this.fee);
        }

        // Categorizar automáticamente basado en MCC
        if (this.category == null && this.merchantMccCode != null) {
            this.category = MccCategoryResolver.resolve(this.merchantMccCode);
        }
    }

    // Métodos de negocio
    public void process() {
        if (this.status != TransactionStatus.PENDING) {
            throw new TransactionStatusException(this.transactionCode,this.status,TransactionStatus.PROCESSING);
        }
        this.status = TransactionStatus.PROCESSING;
        this.processedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status != TransactionStatus.PROCESSING) {
            throw new TransactionStatusException(this.transactionCode,this.status,TransactionStatus.COMPLETED);
        }
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Transitions a fraud-reviewed APPROVED transaction to COMPLETED.
     * Called by approveTransaction() after human review.
     * complete() is untouched — it guards on PROCESSING.
     */
    public void completeFromApproved() {
        if (this.status != TransactionStatus.APPROVED) {
            throw new TransactionStatusException(this.transactionCode, this.status, TransactionStatus.COMPLETED);
        }
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        if(this.status != TransactionStatus.PROCESSING) {
            throw new TransactionStatusException(this.transactionCode,this.status,TransactionStatus.FAILED);
        }
        this.status = TransactionStatus.FAILED;
        this.rejectionReason = reason;
    }

    public void reverse(String reason) {
        if (this.status != TransactionStatus.COMPLETED) {
            throw new TransactionStatusException(this.transactionCode,this.status,TransactionStatus.REVERSED);
        }
        this.reversedAt = LocalDateTime.now();
        this.reversalReason = reason;
        this.status = TransactionStatus.REVERSED;
    }

    public void flagForFraud(BigDecimal score, String reason) {
        this.flaggedForFraud = true;
        this.fraudScore = score;
        this.fraudReason = reason;
        this.status = TransactionStatus.PENDING_REVIEW;
    }

    public void approve(String approver) {
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
        this.status = TransactionStatus.APPROVED;
    }

    public boolean isTransfer() {
        return this.fromAccount != null && this.toAccount != null;
    }

    public boolean isDeposit() {
        return this.fromAccount == null && this.toAccount != null;
    }

    public boolean isWithdrawal() {
        return this.fromAccount != null && this.toAccount == null;
    }

    public boolean canBeReversed() {
        return this.status == TransactionStatus.COMPLETED
                && this.reversedAt == null
                && this.createdAt.isAfter(LocalDateTime.now().minusDays(90));  // 90 días max
    }

    private String generateIdempotencyKey() {
        String data = String.format("%s:%s:%s:%s",
                this.fromAccount != null ? this.fromAccount.getId() : "NONE",
                this.toAccount != null ? this.toAccount.getId() : "NONE",
                this.amount,
                this.createdAt
        );
        return HashUtils.hashSha256(data);
    }
}