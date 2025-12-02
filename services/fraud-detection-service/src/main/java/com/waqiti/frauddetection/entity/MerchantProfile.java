package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Merchant Profile Entity
 *
 * Stores merchant behavioral data, transaction patterns, risk metrics,
 * chargeback/refund statistics for fraud detection and merchant risk scoring.
 *
 * PRODUCTION-GRADE ENTITY
 * - Optimistic locking with @Version
 * - Audit fields with JPA Auditing
 * - Indexed fields for query performance
 * - BigDecimal precision for all monetary values
 * - Chargeback and refund rate tracking
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Entity
@Table(name = "merchant_profiles", indexes = {
    @Index(name = "idx_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_merchant_risk_level", columnList = "current_risk_level"),
    @Index(name = "idx_chargeback_rate", columnList = "chargeback_rate"),
    @Index(name = "idx_refund_rate", columnList = "refund_rate"),
    @Index(name = "idx_merchant_last_transaction", columnList = "last_transaction_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic locking version for concurrent update protection
     */
    @Version
    private Long version;

    /**
     * Merchant ID (reference to merchant service)
     */
    @Column(name = "merchant_id", nullable = false, unique = true)
    private UUID merchantId;

    /**
     * Merchant Information
     */
    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "merchant_category_code", length = 10)
    private String merchantCategoryCode;

    @Column(name = "business_type", length = 50)
    private String businessType;

    /**
     * Transaction Statistics
     */
    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Long totalTransactions = 0L;

    @Column(name = "total_transaction_volume", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalTransactionVolume = BigDecimal.ZERO;

    @Column(name = "average_transaction_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal averageTransactionAmount = BigDecimal.ZERO;

    @Column(name = "max_transaction_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal maxTransactionAmount = BigDecimal.ZERO;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "last_transaction_amount", precision = 19, scale = 4)
    private BigDecimal lastTransactionAmount;

    /**
     * Chargeback Metrics (CRITICAL for merchant risk)
     */
    @Column(name = "chargeback_count", nullable = false)
    @Builder.Default
    private Integer chargebackCount = 0;

    @Column(name = "total_chargeback_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalChargebackAmount = BigDecimal.ZERO;

    @Column(name = "chargeback_rate", precision = 10, scale = 6, nullable = false)
    @Builder.Default
    private BigDecimal chargebackRate = BigDecimal.ZERO;

    @Column(name = "last_chargeback_date")
    private LocalDateTime lastChargebackDate;

    /**
     * Refund Metrics
     */
    @Column(name = "refund_count", nullable = false)
    @Builder.Default
    private Integer refundCount = 0;

    @Column(name = "total_refund_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalRefundAmount = BigDecimal.ZERO;

    @Column(name = "refund_rate", precision = 10, scale = 6, nullable = false)
    @Builder.Default
    private BigDecimal refundRate = BigDecimal.ZERO;

    @Column(name = "last_refund_date")
    private LocalDateTime lastRefundDate;

    /**
     * Fraud Metrics
     */
    @Column(name = "fraud_transaction_count", nullable = false)
    @Builder.Default
    private Integer fraudTransactionCount = 0;

    @Column(name = "total_fraud_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalFraudAmount = BigDecimal.ZERO;

    @Column(name = "fraud_rate", precision = 10, scale = 6, nullable = false)
    @Builder.Default
    private BigDecimal fraudRate = BigDecimal.ZERO;

    @Column(name = "last_fraud_date")
    private LocalDateTime lastFraudDate;

    /**
     * Risk Assessment
     */
    @Column(name = "average_risk_score", nullable = false)
    @Builder.Default
    private Double averageRiskScore = 0.0;

    @Column(name = "current_risk_level", nullable = false, length = 20)
    @Builder.Default
    private String currentRiskLevel = "LOW";

    /**
     * Compliance Status
     */
    @Column(name = "kyb_status", length = 20)
    private String kybStatus; // Know Your Business

    @Column(name = "kyb_completion_date")
    private LocalDateTime kybCompletionDate;

    @Column(name = "enhanced_monitoring_required")
    @Builder.Default
    private Boolean enhancedMonitoringRequired = false;

    /**
     * Merchant Performance
     */
    @Column(name = "settlement_accuracy_rate", precision = 5, scale = 2)
    private BigDecimal settlementAccuracyRate;

    @Column(name = "dispute_win_rate", precision = 5, scale = 2)
    private BigDecimal disputeWinRate;

    /**
     * Audit Fields
     */
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
}
