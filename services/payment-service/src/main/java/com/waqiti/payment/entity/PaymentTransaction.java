package com.waqiti.payment.entity;

import com.waqiti.common.entity.BaseEntity;
import lombok.*;
import org.hibernate.envers.Audited;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a comprehensive payment transaction
 */
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_payer_id", columnList = "payer_id"),
    @Index(name = "idx_payee_id", columnList = "payee_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_payment_type", columnList = "payment_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_reservation_id", columnList = "reservation_id"),
    @Index(name = "idx_payment_payer_status", columnList = "payer_id, status"),
    @Index(name = "idx_payment_payee_status", columnList = "payee_id, status"),
    @Index(name = "idx_payment_status_created", columnList = "status, created_at"),
    @Index(name = "idx_payment_fraud_status", columnList = "fraud_score, status"),
    @Index(name = "idx_payment_risk_created", columnList = "risk_level, created_at"),
    @Index(name = "idx_payment_provider_status", columnList = "provider_name, status"),
    // PRODUCTION OPTIMIZATION: Additional critical indexes for query performance
    @Index(name = "idx_transaction_user_date", columnList = "payer_id, created_at DESC"),
    @Index(name = "idx_transaction_status_created", columnList = "status, created_at DESC"),
    @Index(name = "idx_payment_method_user_active", columnList = "payee_id, status, created_at DESC")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class PaymentTransaction extends BaseEntity {

    @Column(name = "transaction_id", nullable = false, unique = true, length = 255)
    private String transactionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 50)
    private PaymentType paymentType;
    
    @Column(name = "payer_id", nullable = false)
    private UUID payerId;
    
    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "processing_fee", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal processingFee = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PaymentStatus status;
    
    // Authorization details
    @Column(name = "authorization_code", length = 255)
    private String authorizationCode;
    
    @Column(name = "processor_response_code", length = 100)
    private String processorResponseCode;
    
    @Column(name = "authorized_at")
    private Instant authorizedAt;
    
    // Settlement details
    @Column(name = "settlement_id", length = 255)
    private String settlementId;
    
    @Column(name = "settled_at")
    private Instant settledAt;
    
    @Column(name = "actual_settlement_amount", precision = 19, scale = 4)
    private BigDecimal actualSettlementAmount;
    
    // Processing details
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;
    
    @Column(name = "provider_name", length = 100)
    private String providerName;
    
    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;
    
    // Fraud and risk assessment
    @Column(name = "fraud_score", precision = 5, scale = 4)
    private BigDecimal fraudScore;
    
    @Column(name = "risk_level", length = 50)
    private String riskLevel;
    
    @ElementCollection
    @CollectionTable(name = "payment_transaction_risk_factors",
            joinColumns = @JoinColumn(name = "transaction_id"))
    @Column(name = "risk_factor")
    private java.util.List<String> riskFactors;
    
    @Column(name = "compliance_flags", columnDefinition = "TEXT")
    private String complianceFlags;
    
    @ElementCollection
    @CollectionTable(name = "payment_transaction_aml_flags",
            joinColumns = @JoinColumn(name = "transaction_id"))
    @Column(name = "aml_flag")
    private java.util.List<String> amlFlags;
    
    // Balance reservation
    @Column(name = "reservation_id", length = 255)
    private String reservationId;
    
    @Column(name = "reserved_at")
    private Instant reservedAt;
    
    // Error handling
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "error_code", length = 100)
    private String errorCode;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // Retry mechanism
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "last_retry_at")
    private Instant lastRetryAt;
    
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;
    
    // P2P specific fields
    @Column(name = "sender_id")
    private String senderId;
    
    @Column(name = "recipient_id")
    private String recipientId;
    
    @Column(name = "transfer_message", columnDefinition = "TEXT")
    private String transferMessage;
    
    @Column(name = "transfer_reference", length = 255)
    private String transferReference;
    
    // Transaction context
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "reference", length = 255)
    private String reference;
    
    @Column(name = "merchant_reference", length = 255)
    private String merchantReference;
    
    // Device and location
    @Column(name = "device_id", length = 255)
    private String deviceId;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @Column(name = "location_name", length = 255)
    private String locationName;
    
    // Additional metadata
    @ElementCollection
    @CollectionTable(name = "payment_transaction_metadata",
            joinColumns = @JoinColumn(name = "transaction_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", columnDefinition = "TEXT")
    private Map<String, String> metadata;
    
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    /**
     * Check if transaction is completed
     */
    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }
    
    /**
     * Check if transaction is failed
     */
    public boolean isFailed() {
        return status == PaymentStatus.FAILED || 
               status == PaymentStatus.SETTLEMENT_FAILED ||
               status == PaymentStatus.AUTHORIZATION_FAILED;
    }
    
    /**
     * Check if transaction is pending
     */
    public boolean isPending() {
        return status == PaymentStatus.INITIATED || 
               status == PaymentStatus.AUTHORIZED ||
               status == PaymentStatus.PENDING;
    }
    
    /**
     * Check if transaction can be retried
     */
    public boolean canRetry() {
        return isFailed() && 
               retryCount < maxRetries && 
               (lastRetryAt == null || 
                lastRetryAt.isBefore(Instant.now().minusSeconds(300))); // 5 minute cooldown
    }
    
    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = Instant.now();
    }
    
    /**
     * Get total amount including fees
     */
    public BigDecimal getTotalAmount() {
        return amount.add(processingFee != null ? processingFee : BigDecimal.ZERO);
    }
    
    /**
     * Mark as high risk
     */
    public void markAsHighRisk(BigDecimal score, java.util.List<String> factors) {
        this.fraudScore = score;
        this.riskLevel = "HIGH";
        this.riskFactors = factors;
    }
}