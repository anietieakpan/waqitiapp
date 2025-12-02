package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event triggered when a merchant settlement fails
 * Critical for merchant operations, financial reconciliation, and support escalation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettlementFailedEvent implements DomainEvent {
    
    private String eventId;
    private Instant timestamp;
    private String settlementId;
    private String merchantId;
    private String merchantName;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;
    private BigDecimal totalFees;
    private BigDecimal totalTax;
    private String currency;
    private Integer transactionCount;
    private String settlementPeriod; // e.g., "2025-01-01 to 2025-01-31"
    
    // Failure details
    private String failureReason;
    private String failureCategory; // COMPLIANCE, INSUFFICIENT_FUNDS, BANK_TRANSFER, VALIDATION, SYSTEM_ERROR
    private String failureCode;
    private String bankTransferId; // If failure occurred during bank transfer
    private String bankErrorCode;
    private String bankErrorMessage;
    
    // Merchant context
    private String bankAccountId;
    private String bankAccountStatus;
    private boolean merchantAccountSuspended;
    private boolean settlementSuspended;
    
    // Retry information
    private Integer retryAttempt;
    private Integer maxRetryAttempts;
    private boolean retryable;
    private Instant nextRetryAt;
    
    // Compliance flags
    private boolean complianceViolation;
    private String complianceFlag;
    private boolean amlViolation;
    private boolean sanctionsViolation;
    private boolean kycNonCompliant;
    
    // Risk information
    private Integer riskScore;
    private boolean highRisk;
    
    // Impact assessment
    private boolean affectsMerchantCashFlow;
    private boolean requiresImmediateAction;
    private String impactLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    // Additional context
    private Map<String, Object> additionalData;
    private String correlationId;
    
    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public String getUserId() {
        return merchantId; // Using merchantId as the user identifier
    }

    @Override
    public String getEventType() {
        return "MerchantSettlementFailedEvent";
    }

    @Override
    public String getAggregateId() {
        return settlementId;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return additionalData;
    }

    @Override
    public String getTopic() {
        return "merchant-settlements";
    }

    @Override
    public String getAggregateType() {
        return "MerchantSettlement";
    }

    @Override
    public String getAggregateName() {
        return "Merchant Settlement";
    }

    @Override
    public Long getVersion() {
        return 1L;
    }

    @Override
    public String getSourceService() {
        return "merchant-service";
    }
}