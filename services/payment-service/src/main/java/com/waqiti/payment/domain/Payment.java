package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment entity - MongoDB document
 * CRITICAL: Added @Version for optimistic locking to prevent concurrent modification issues
 * MongoDB will automatically handle version conflicts and throw OptimisticLockingFailureException
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class Payment {
    @Id
    private String id;

    /**
     * Optimistic locking version field for MongoDB
     * Prevents lost updates when multiple processes modify the same payment concurrently
     */
    @Version
    private Long version;
    private String userId;
    private String merchantId;
    
    // Payment details
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String provider;
    private String description;
    private String referenceNumber;
    
    // Fee details
    private BigDecimal processingFee;
    private BigDecimal platformFee;
    private BigDecimal totalFees;
    private BigDecimal netAmount;
    private String feeCalculationId;
    
    // Settlement details
    private String settlementId;
    private String settlementStatus;
    private LocalDate settlementDate;
    private BigDecimal settledAmount;
    
    // Refund details
    private boolean refunded;
    private BigDecimal refundedAmount;
    private String refundStatus;
    private LocalDateTime refundedAt;
    private String refundReason;
    
    // Chargeback details
    private boolean hasChargeback;
    private String chargebackId;
    private String chargebackStatus;
    private BigDecimal chargebackAmount;
    
    // Currency conversion
    private String conversionId;
    private BigDecimal convertedAmount;
    private BigDecimal conversionRate;
    private String targetCurrency;
    
    // Risk assessment
    private Integer fraudScore;
    private boolean threeDSecure;
    private String riskLevel;
    
    // Metadata
    private Map<String, String> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional fields for compatibility
    private String transactionId;
    private String customerId;
    private String orderId;
    private String invoiceId;
    
    // Payment source
    private String cardLast4;
    private String cardBrand;
    private String bankAccount;
    
    // Status tracking
    private LocalDateTime authorizedAt;
    private LocalDateTime capturedAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private String failureCode;
    
    // Compliance
    private boolean amlScreened;
    private boolean sanctionsChecked;
    private String complianceStatus;
    
    // Recurring payment
    private boolean isRecurring;
    private String subscriptionId;
    private String recurringSchedule;
}