package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Transaction information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    
    private UUID transactionId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String status;
    private String description;
    private String reference;
    private String externalReference;
    private UUID userId;
    private UUID merchantId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime scheduledAt;
    private String failureReason;
    private BigDecimal fees;
    private String feeType;
    private String paymentMethod;
    private String paymentProvider;
    private String authorizationCode;
    private String settlementDate;
    private Map<String, Object> metadata;
    
    // Compliance fields
    private String complianceStatus;
    private String riskScore;
    private String kycStatus;
    private String amlStatus;
    
    // Geographic information
    private String sourceCountry;
    private String destinationCountry;
    private String ipAddress;
    private String userAgent;
    
    // Processing information
    private int retryCount;
    private String processingNode;
    private Long processingTimeMs;
    private String batchId;
    
    // Notification fields
    private boolean smsNotificationSent;
    private boolean emailNotificationSent;
    private boolean pushNotificationSent;
    
    // Audit fields
    private String createdBy;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    private Integer version;
    
    /**
     * Transaction types
     */
    public enum TransactionType {
        TRANSFER,
        PAYMENT,
        DEPOSIT,
        WITHDRAWAL,
        REFUND,
        CHARGEBACK,
        FEE,
        INTEREST,
        ADJUSTMENT
    }
    
    /**
     * Transaction statuses
     */
    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED,
        EXPIRED
    }
    
    /**
     * Check if transaction is completed
     */
    public boolean isCompleted() {
        return Status.COMPLETED.name().equals(status);
    }
    
    /**
     * Check if transaction is pending
     */
    public boolean isPending() {
        return Status.PENDING.name().equals(status);
    }
    
    /**
     * Check if transaction has failed
     */
    public boolean hasFailed() {
        return Status.FAILED.name().equals(status) || Status.CANCELLED.name().equals(status);
    }
    
    /**
     * Check if transaction involves fees
     */
    public boolean hasFees() {
        return fees != null && fees.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get net amount after fees
     */
    public BigDecimal getNetAmount() {
        if (fees == null) {
            return amount;
        }
        return amount.subtract(fees);
    }
    
    /**
     * Check if transaction is high value
     */
    public boolean isHighValue() {
        return amount != null && amount.compareTo(new BigDecimal("10000")) >= 0;
    }
    
    /**
     * Check if transaction is cross-border
     */
    public boolean isCrossBorder() {
        return sourceCountry != null && destinationCountry != null && 
               !sourceCountry.equals(destinationCountry);
    }
    
    /**
     * Get transaction duration in milliseconds
     */
    public Long getDurationMs() {
        if (createdAt != null && completedAt != null) {
            return java.time.Duration.between(createdAt, completedAt).toMillis();
        }
        return null;
    }
}