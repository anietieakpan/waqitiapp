package com.waqiti.transaction.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transaction Entity
 * 
 * Represents a financial transaction in the system.
 * This entity tracks the complete lifecycle of a transaction
 * from initiation to completion or failure.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_from_wallet", columnList = "fromWalletId"),
    @Index(name = "idx_to_wallet", columnList = "toWalletId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_reference", columnList = "reference", unique = true),
    @Index(name = "idx_from_wallet_status", columnList = "fromWalletId,status"),
    @Index(name = "idx_to_wallet_status", columnList = "toWalletId,status"),
    @Index(name = "idx_external_reference", columnList = "externalReference")
})
@EntityListeners(AuditingEntityListener.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @NotNull
    @Column(nullable = false, unique = true)
    private String reference;

    @NotNull
    @Column(nullable = false)
    private String fromWalletId;

    @NotNull
    @Column(nullable = false)
    private String toWalletId;

    @Column(nullable = false)
    private String fromUserId;

    @Column(nullable = false)
    private String toUserId;
    
    // Account-based fields for banking transactions
    @Column
    private String sourceAccountId;
    
    @Column
    private String targetAccountId;
    
    // Saga orchestration fields
    @Column
    private String sagaId;
    
    @Column
    private String processingResult;
    
    // Additional transaction fields
    @Column
    private String initiatedBy;
    
    @Column
    private String channel;
    
    @Column
    private Double fraudScore;
    
    @Column
    private LocalDateTime reconciledAt;
    
    @Column
    private String reconciliationStatus;
    
    @Column
    private String reconciliationNotes;
    
    @Column
    private LocalDateTime retrySuccessfulAt;
    
    @Column
    private String successfulRetryTransactionId;
    
    @Column
    private LocalDateTime rolledBackAt;
    
    @Column
    private String batchId;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "transaction_metadata", joinColumns = @JoinColumn(name = "transaction_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, String> metadata;

    // Fees and charges
    @Column(precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalAmount;

    // Processing information
    @Column
    private String processorReference;

    @Column
    private String externalReference;

    @Column
    private LocalDateTime processedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime failedAt;

    @Column
    private LocalDateTime reversedAt;

    // Error handling
    @Column
    private String failureReason;

    @Column
    private String failureCode;

    @Column
    private Integer retryCount = 0;

    @Column
    private LocalDateTime nextRetryAt;

    // Suspension fields
    @Column
    private String suspensionReason;
    
    @Column
    private LocalDateTime suspendedAt;
    
    @Column
    private Boolean emergencySuspension = false;
    
    @Column
    private String customerId;
    
    @Column
    private String merchantId;
    
    @Column
    private String merchantName;
    
    @Column
    private String category;
    
    @Column
    private LocalDateTime blockedAt;
    
    @Column
    private String blockReason;
    
    @Column
    private String blockId;
    
    @Column
    private String userId;
    
    @Column
    private String originCountry;

    // Audit fields
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private String createdBy;

    @Column
    private String updatedBy;

    // Version for optimistic locking
    @Version
    private Long version;

    // Transaction history
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<TransactionEvent> events = new ArrayList<>();

    // Constructors
    public Transaction() {
        this.status = TransactionStatus.INITIATED;
        this.retryCount = 0;
    }

    // Business methods
    public void addEvent(TransactionEventType eventType, String description, String details) {
        TransactionEvent event = new TransactionEvent(this, eventType, description, details);
        this.events.add(event);
    }

    public void calculateTotalAmount() {
        BigDecimal feeAmount = fee != null ? fee : BigDecimal.ZERO;
        BigDecimal taxAmount = tax != null ? tax : BigDecimal.ZERO;
        this.totalAmount = amount.add(feeAmount).add(taxAmount);
    }

    public boolean canRetry() {
        return retryCount < 3 && (status == TransactionStatus.FAILED || status == TransactionStatus.PROCESSING_ERROR);
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.nextRetryAt = LocalDateTime.now().plusMinutes((long) Math.pow(2, retryCount));
    }

    public boolean isTerminal() {
        return status == TransactionStatus.COMPLETED || 
               status == TransactionStatus.REVERSED || 
               status == TransactionStatus.CANCELLED;
    }

    // Getters and Setters
    public java.util.UUID getId() {
        return id;
    }

    public void setId(java.util.UUID id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getFromWalletId() {
        return fromWalletId;
    }

    public void setFromWalletId(String fromWalletId) {
        this.fromWalletId = fromWalletId;
    }

    public String getToWalletId() {
        return toWalletId;
    }

    public void setToWalletId(String toWalletId) {
        this.toWalletId = toWalletId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }
    
    // Alias for compatibility
    public TransactionType getTransactionType() {
        return type;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.type = transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getProcessorReference() {
        return processorReference;
    }

    public void setProcessorReference(String processorReference) {
        this.processorReference = processorReference;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
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

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(LocalDateTime reversedAt) {
        this.reversedAt = reversedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<TransactionEvent> getEvents() {
        return events;
    }

    public void setEvents(List<TransactionEvent> events) {
        this.events = events;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(String sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(String targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getProcessingResult() {
        return processingResult;
    }

    public void setProcessingResult(String processingResult) {
        this.processingResult = processingResult;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Double getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(Double fraudScore) {
        this.fraudScore = fraudScore;
    }

    public LocalDateTime getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(LocalDateTime reconciledAt) {
        this.reconciledAt = reconciledAt;
    }

    public String getReconciliationStatus() {
        return reconciliationStatus;
    }

    public void setReconciliationStatus(String reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public String getReconciliationNotes() {
        return reconciliationNotes;
    }

    public void setReconciliationNotes(String reconciliationNotes) {
        this.reconciliationNotes = reconciliationNotes;
    }

    public LocalDateTime getRetrySuccessfulAt() {
        return retrySuccessfulAt;
    }

    public void setRetrySuccessfulAt(LocalDateTime retrySuccessfulAt) {
        this.retrySuccessfulAt = retrySuccessfulAt;
    }

    public String getSuccessfulRetryTransactionId() {
        return successfulRetryTransactionId;
    }

    public void setSuccessfulRetryTransactionId(String successfulRetryTransactionId) {
        this.successfulRetryTransactionId = successfulRetryTransactionId;
    }

    public LocalDateTime getRolledBackAt() {
        return rolledBackAt;
    }

    public void setRolledBackAt(LocalDateTime rolledBackAt) {
        this.rolledBackAt = rolledBackAt;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public void setSuspensionReason(String suspensionReason) {
        this.suspensionReason = suspensionReason;
    }

    public LocalDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(LocalDateTime suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public Boolean getEmergencySuspension() {
        return emergencySuspension;
    }

    public void setEmergencySuspension(Boolean emergencySuspension) {
        this.emergencySuspension = emergencySuspension;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getMerchantName() {
        return merchantName;
    }
    
    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }
    
    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }
    
    public String getBlockReason() {
        return blockReason;
    }
    
    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }
    
    public String getBlockId() {
        return blockId;
    }
    
    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getOriginCountry() {
        return originCountry;
    }
    
    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Transaction{" +
               "id='" + id + '\'' +
               ", reference='" + reference + '\'' +
               ", fromWalletId='" + fromWalletId + '\'' +
               ", toWalletId='" + toWalletId + '\'' +
               ", amount=" + amount +
               ", currency='" + currency + '\'' +
               ", type=" + type +
               ", status=" + status +
               ", createdAt=" + createdAt +
               '}';
    }
}