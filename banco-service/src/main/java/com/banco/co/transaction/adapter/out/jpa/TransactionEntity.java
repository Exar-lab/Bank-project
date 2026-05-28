package com.banco.co.transaction.adapter.out.jpa;

import com.banco.co.account.adapter.out.jpa.AccountEntity;
import com.banco.co.card.model.Card;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.transaction.enums.FeeType;
import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionSubcategory;
import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.transaction.generator.TransactionCodeGenerator;
import com.banco.co.transaction.resolver.MccCategoryResolver;
import com.banco.co.security.securityhasher.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Anemic JPA entity — only JPA annotations + getters/setters, ZERO business logic.
 * Maps to the same 'transactions' table as the legacy com.banco.co.transaction.model.Transaction entity.
 * Cross-feature JPA relationships (AccountEntity, Card, Envelope) are kept for DB integrity.
 * The domain model uses IDs only — the adapter layer translates between them.
 * @PrePersist handles generated columns only (code, idempotency key, netAmount, category from MCC).
 * Business state transitions (process, complete, fail, etc.) are NOT here — they live in the domain model.
 */
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
class TransactionEntity {

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

    // Cuentas involucradas — JPA entity references for DB integrity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private AccountEntity fromAccount;  // Cuenta de origen (puede ser null en depósitos)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private AccountEntity toAccount;  // Cuenta de destino (puede ser null en retiros)

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
    private TransactionEntity originalTransaction;  // Si es reversión, apunta a la original

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

    // ══════════════════════════════════════════════════════════
    // Generated columns only — NO business logic, NO state transitions.
    // Business methods (process, complete, fail, etc.) live in the domain model.
    // ══════════════════════════════════════════════════════════
    @PrePersist
    public void generateTransactionData() {
        if (this.transactionCode == null) {
            this.transactionCode = TransactionCodeGenerator.generate();
        }

        // Generar idempotency key si no existe
        if (this.idempotencyKey == null) {
            String data = String.format("%s:%s:%s:%s",
                    this.fromAccount != null ? this.fromAccount.getId() : "NONE",
                    this.toAccount != null ? this.toAccount.getId() : "NONE",
                    this.amount,
                    this.createdAt
            );
            this.idempotencyKey = HashUtils.hashSha256(data);
        }

        // Calcular monto neto
        if (this.netAmount == null && this.amount != null && this.fee != null) {
            this.netAmount = this.amount.subtract(this.fee);
        }

        // Categorizar automáticamente basado en MCC
        if (this.category == null && this.merchantMccCode != null) {
            this.category = MccCategoryResolver.resolve(this.merchantMccCode);
        }
    }

    // ══════════════════════════════════════════════════════════
    // Getters and Setters — no Lombok @Data on @Entity
    // ══════════════════════════════════════════════════════════

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public TransactionChannel getChannel() {
        return channel;
    }

    public void setChannel(TransactionChannel channel) {
        this.channel = channel;
    }

    public AccountEntity getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(AccountEntity fromAccount) {
        this.fromAccount = fromAccount;
    }

    public AccountEntity getToAccount() {
        return toAccount;
    }

    public void setToAccount(AccountEntity toAccount) {
        this.toAccount = toAccount;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public String getCardLastFourDigits() {
        return cardLastFourDigits;
    }

    public void setCardLastFourDigits(String cardLastFourDigits) {
        this.cardLastFourDigits = cardLastFourDigits;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public void setFeeType(FeeType feeType) {
        this.feeType = feeType;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public BigDecimal getFromAccountBalanceBefore() {
        return fromAccountBalanceBefore;
    }

    public void setFromAccountBalanceBefore(BigDecimal fromAccountBalanceBefore) {
        this.fromAccountBalanceBefore = fromAccountBalanceBefore;
    }

    public BigDecimal getFromAccountBalanceAfter() {
        return fromAccountBalanceAfter;
    }

    public void setFromAccountBalanceAfter(BigDecimal fromAccountBalanceAfter) {
        this.fromAccountBalanceAfter = fromAccountBalanceAfter;
    }

    public BigDecimal getToAccountBalanceBefore() {
        return toAccountBalanceBefore;
    }

    public void setToAccountBalanceBefore(BigDecimal toAccountBalanceBefore) {
        this.toAccountBalanceBefore = toAccountBalanceBefore;
    }

    public BigDecimal getToAccountBalanceAfter() {
        return toAccountBalanceAfter;
    }

    public void setToAccountBalanceAfter(BigDecimal toAccountBalanceAfter) {
        this.toAccountBalanceAfter = toAccountBalanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    public String getMerchantCountry() {
        return merchantCountry;
    }

    public void setMerchantCountry(String merchantCountry) {
        this.merchantCountry = merchantCountry;
    }

    public String getMerchantMccCode() {
        return merchantMccCode;
    }

    public void setMerchantMccCode(String merchantMccCode) {
        this.merchantMccCode = merchantMccCode;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public void setCategory(TransactionCategory category) {
        this.category = category;
    }

    public TransactionSubcategory getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(TransactionSubcategory subcategory) {
        this.subcategory = subcategory;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getLocationCity() {
        return locationCity;
    }

    public void setLocationCity(String locationCity) {
        this.locationCity = locationCity;
    }

    public String getLocationCountry() {
        return locationCountry;
    }

    public void setLocationCountry(String locationCountry) {
        this.locationCountry = locationCountry;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(LocalDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(LocalDateTime reversedAt) {
        this.reversedAt = reversedAt;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public void setReversalReason(String reversalReason) {
        this.reversalReason = reversalReason;
    }

    public TransactionEntity getOriginalTransaction() {
        return originalTransaction;
    }

    public void setOriginalTransaction(TransactionEntity originalTransaction) {
        this.originalTransaction = originalTransaction;
    }

    public boolean isReversal() {
        return isReversal;
    }

    public void setReversal(boolean reversal) {
        isReversal = reversal;
    }

    public boolean isFlaggedForFraud() {
        return flaggedForFraud;
    }

    public void setFlaggedForFraud(boolean flaggedForFraud) {
        this.flaggedForFraud = flaggedForFraud;
    }

    public BigDecimal getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(BigDecimal fraudScore) {
        this.fraudScore = fraudScore;
    }

    public String getFraudReason() {
        return fraudReason;
    }

    public void setFraudReason(String fraudReason) {
        this.fraudReason = fraudReason;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Envelope getEnvelope() {
        return envelope;
    }

    public void setEnvelope(Envelope envelope) {
        this.envelope = envelope;
    }
}
