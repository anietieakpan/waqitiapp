package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Limit Model
 * 
 * CRITICAL: Represents transaction limits for users based on KYC tiers and risk profiles.
 * Supports regulatory compliance and risk management.
 * 
 * LIMIT TYPES:
 * - DAILY_SEND: Daily sending limit
 * - DAILY_RECEIVE: Daily receiving limit
 * - MONTHLY_SEND: Monthly sending limit
 * - MONTHLY_RECEIVE: Monthly receiving limit
 * - SINGLE_TRANSACTION: Per transaction limit
 * - CASH_EQUIVALENT: Cash equivalent transaction limit
 * 
 * COMPLIANCE IMPACT:
 * - Supports BSA transaction monitoring requirements
 * - Enables risk-based transaction limits
 * - Maintains audit trail for limit changes
 * - Supports KYC tier-based restrictions
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "transaction_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TransactionLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String limitType; // DAILY_SEND, DAILY_RECEIVE, MONTHLY_SEND, etc.

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal limitAmount;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private String period; // DAILY, WEEKLY, MONTHLY, YEARLY

    // KYC and tier information
    private String kycTier;
    private String riskRating;

    // Limit metadata
    private String limitCategory; // REGULATORY, RISK_BASED, CUSTOMER_REQUESTED
    private String limitSource; // SYSTEM, MANUAL, API
    private boolean isActive = true;
    private boolean isSystemGenerated = true;

    // Effective period
    @Column(nullable = false)
    private LocalDateTime effectiveDate;
    private LocalDateTime expiryDate;

    // Usage tracking
    private BigDecimal currentUsage = BigDecimal.ZERO;
    private LocalDateTime lastUsageUpdate;
    private Integer usageCount = 0;

    // Override capabilities
    private boolean allowTemporaryOverride = false;
    private BigDecimal temporaryOverrideAmount;
    private LocalDateTime temporaryOverrideExpiry;
    private String overrideReason;
    private UUID overrideApprovedBy;

    // Regulatory compliance
    private boolean requiresReporting = false;
    private String reportingThresholdType; // CTR, SAR, etc.
    private boolean exemptFromLimits = false;
    private String exemptionReason;

    // Escalation rules
    private boolean requiresApprovalAboveLimit = false;
    private String approvalWorkflow;
    private BigDecimal warningThresholdPercentage = BigDecimal.valueOf(80); // 80%

    // Audit fields
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    // Business logic methods

    public boolean isExpired() {
        return expiryDate != null && LocalDateTime.now().isAfter(expiryDate);
    }

    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();
        return isActive && !isExpired() && 
               (effectiveDate == null || !now.isBefore(effectiveDate));
    }

    public BigDecimal getRemainingLimit() {
        if (!isEffective()) {
            return BigDecimal.ZERO;
        }
        
        return limitAmount.subtract(currentUsage).max(BigDecimal.ZERO);
    }

    public BigDecimal getUsagePercentage() {
        if (limitAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return currentUsage.multiply(BigDecimal.valueOf(100))
                          .divide(limitAmount, 2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isNearLimit() {
        return getUsagePercentage().compareTo(warningThresholdPercentage) >= 0;
    }

    public boolean isAtLimit() {
        return currentUsage.compareTo(limitAmount) >= 0;
    }

    public boolean canAccommodateAmount(BigDecimal amount) {
        if (!isEffective() || amount == null) {
            return false;
        }
        
        BigDecimal remainingLimit = getRemainingLimit();
        return remainingLimit.compareTo(amount) >= 0;
    }

    public boolean hasTemporaryOverride() {
        return allowTemporaryOverride && 
               temporaryOverrideAmount != null && 
               temporaryOverrideExpiry != null &&
               LocalDateTime.now().isBefore(temporaryOverrideExpiry);
    }

    public BigDecimal getEffectiveLimit() {
        if (hasTemporaryOverride()) {
            return temporaryOverrideAmount;
        }
        return limitAmount;
    }

    public boolean isDailyLimit() {
        return "DAILY".equals(period);
    }

    public boolean isMonthlyLimit() {
        return "MONTHLY".equals(period);
    }

    public boolean isSendingLimit() {
        return limitType.contains("SEND");
    }

    public boolean isReceivingLimit() {
        return limitType.contains("RECEIVE");
    }

    public boolean requiresRegulatoryReporting() {
        return requiresReporting && reportingThresholdType != null;
    }

    public String getLimitSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(limitType).append(": ");
        summary.append(currency).append(" ").append(limitAmount);
        summary.append(" (").append(period).append(")");
        
        if (hasTemporaryOverride()) {
            summary.append(" [Override: ").append(temporaryOverrideAmount).append("]");
        }
        
        summary.append(" - Used: ").append(getUsagePercentage()).append("%");
        
        return summary.toString();
    }

    @PrePersist
    protected void onCreate() {
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }
        if (currentUsage == null) {
            currentUsage = BigDecimal.ZERO;
        }
        if (usageCount == null) {
            usageCount = 0;
        }
        if (warningThresholdPercentage == null) {
            warningThresholdPercentage = BigDecimal.valueOf(80);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}