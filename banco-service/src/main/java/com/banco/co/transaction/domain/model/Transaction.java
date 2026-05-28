package com.banco.co.transaction.domain.model;

import com.banco.co.transaction.enums.FeeType;
import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.enums.TransactionSubcategory;
import com.banco.co.transaction.enums.TransactionType;
import com.banco.co.transaction.exception.transaction.TransactionStatusException;
import com.banco.co.transaction.generator.TransactionCodeGenerator;
import com.banco.co.transaction.resolver.MccCategoryResolver;
import com.banco.co.security.securityhasher.HashUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pure domain model — ZERO JPA, ZERO Spring imports.
 * Cross-feature references use UUID only:
 *   - fromAccountId / toAccountId instead of Account entity
 *   - cardId instead of Card entity
 *   - envelopeId instead of Envelope entity
 *   - originalTransactionId instead of Transaction self-reference
 * Business methods preserved exactly from transaction/model/Transaction.java.
 * Coexists with com.banco.co.transaction.model.Transaction during the additive migration phase.
 */
public class Transaction {

    private UUID id;

    // Identificador único visible
    private String transactionCode;  // TXN-BCR-2024-X7K9P2M3

    // Idempotencia (evitar duplicados)
    private String idempotencyKey;  // Hash único para evitar duplicados

    private String externalReference;  // Referencia de sistema externo

    // Tipo y estado
    private TransactionType type;  // DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT, etc.

    private TransactionStatus status = TransactionStatus.PENDING;

    private TransactionChannel channel;  // WEB, MOBILE, ATM, BRANCH, POS

    // Cross-feature references — UUID only, no JPA entity dependencies
    private UUID fromAccountId;   // Cuenta de origen (puede ser null en depósitos)
    private UUID toAccountId;     // Cuenta de destino (puede ser null en retiros)
    private UUID cardId;          // Tarjeta usada (si aplica)
    private UUID envelopeId;      // Sobre (si aplica)
    private UUID originalTransactionId;  // Si es reversión, apunta a la original

    private String cardLastFourDigits;  // Últimos 4 dígitos de la tarjeta
    private String authorizationCode;  // Código de autorización

    // Montos
    private BigDecimal amount;  // Monto de la transacción
    private BigDecimal fee = BigDecimal.ZERO;  // Comisión
    private FeeType feeType;  // PERCENTAGE, FIXED, NONE
    private BigDecimal netAmount;  // Monto neto (amount - fee)
    private String currency = "CRC";  // Moneda
    private BigDecimal exchangeRate;  // Si es conversión de moneda

    // Balances antes/después (para auditoría)
    private BigDecimal fromAccountBalanceBefore;
    private BigDecimal fromAccountBalanceAfter;
    private BigDecimal toAccountBalanceBefore;
    private BigDecimal toAccountBalanceAfter;

    // Descripción
    private String description;
    private String notes;  // Notas adicionales

    // Comercio/Merchant (para compras)
    private String merchantName;  // Nombre del comercio
    private String merchantCity;
    private String merchantCountry;
    private String merchantMccCode;  // Código MCC (Merchant Category Code)
    private String merchantCategory;  // RESTAURANT, GAS_STATION, PHARMACY, etc.

    // Categorización para análisis
    private TransactionCategory category;  // FOOD, TRANSPORT, ENTERTAINMENT, etc.
    private TransactionSubcategory subcategory;
    private String tags;  // Etiquetas separadas por comas

    // Ubicación y dispositivo
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private String deviceType;  // ANDROID, IOS, WEB, ATM
    private String locationCity;
    private String locationCountry;
    private Double latitude;
    private Double longitude;

    // Fechas importantes
    private LocalDateTime createdAt;  // Cuando se inició
    private LocalDateTime processedAt;  // Cuando se procesó
    private LocalDateTime completedAt;  // Cuando se completó
    private LocalDateTime scheduledFor;  // Para transacciones programadas

    // Reversión/Cancelación
    private LocalDateTime reversedAt;
    private String reversalReason;
    private boolean isReversal = false;

    // Detección de fraude
    private boolean flaggedForFraud = false;
    private BigDecimal fraudScore;  // Score de riesgo (0-100)
    private String fraudReason;

    // Aprobaciones
    private String approvedBy;  // Username de quien aprobó
    private LocalDateTime approvedAt;
    private String rejectionReason;

    // Metadata adicional (JSON flexible)
    private String metadata;  // JSON con datos extra

    public Transaction() {
    }

    // ══════════════════════════════════════════════════════════
    // Domain lifecycle — replaces @PrePersist logic from legacy entity.
    // Called explicitly by the JPA adapter before persisting.
    // ══════════════════════════════════════════════════════════

    public void initializeTransactionData() {
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

    // ══════════════════════════════════════════════════════════
    // Business methods
    // ══════════════════════════════════════════════════════════

    public void process() {
        if (this.status != TransactionStatus.PENDING) {
            throw new TransactionStatusException(this.transactionCode, this.status, TransactionStatus.PROCESSING);
        }
        this.status = TransactionStatus.PROCESSING;
        this.processedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status != TransactionStatus.PROCESSING) {
            throw new TransactionStatusException(this.transactionCode, this.status, TransactionStatus.COMPLETED);
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
        if (this.status != TransactionStatus.PROCESSING) {
            throw new TransactionStatusException(this.transactionCode, this.status, TransactionStatus.FAILED);
        }
        this.status = TransactionStatus.FAILED;
        this.rejectionReason = reason;
    }

    public void reverse(String reason) {
        if (this.status != TransactionStatus.COMPLETED) {
            throw new TransactionStatusException(this.transactionCode, this.status, TransactionStatus.REVERSED);
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
        return this.fromAccountId != null && this.toAccountId != null;
    }

    public boolean isDeposit() {
        return this.fromAccountId == null && this.toAccountId != null;
    }

    public boolean isWithdrawal() {
        return this.fromAccountId != null && this.toAccountId == null;
    }

    public boolean canBeReversed() {
        return this.status == TransactionStatus.COMPLETED
                && this.reversedAt == null
                && this.createdAt != null
                && this.createdAt.isAfter(LocalDateTime.now().minusDays(90));  // 90 días max
    }

    private String generateIdempotencyKey() {
        String data = String.format("%s:%s:%s:%s",
                this.fromAccountId != null ? this.fromAccountId.toString() : "NONE",
                this.toAccountId != null ? this.toAccountId.toString() : "NONE",
                this.amount,
                this.createdAt
        );
        return HashUtils.hashSha256(data);
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

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(UUID fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(UUID toAccountId) {
        this.toAccountId = toAccountId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public UUID getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(UUID envelopeId) {
        this.envelopeId = envelopeId;
    }

    public UUID getOriginalTransactionId() {
        return originalTransactionId;
    }

    public void setOriginalTransactionId(UUID originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
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
}
