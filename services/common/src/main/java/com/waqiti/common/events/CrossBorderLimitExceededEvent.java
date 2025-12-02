package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event triggered when a cross-border transfer exceeds regulatory or user limits
 * Critical for regulatory compliance, fraud prevention, and user protection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossBorderLimitExceededEvent implements DomainEvent {
    
    private String eventId;
    private Instant timestamp;
    private String transferId;
    private String userId;
    private String accountId;
    
    // Transfer details
    private BigDecimal transferAmount;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal convertedAmount;
    private String sourceCountry;
    private String destinationCountry;
    private String transferPurpose;
    
    // Limit details
    private String limitType; // DAILY, WEEKLY, MONTHLY, ANNUAL, PER_TRANSACTION, REGULATORY
    private String limitCategory; // USER_TIER, REGULATORY, KYC_LEVEL, COUNTRY_SPECIFIC
    private BigDecimal limitAmount;
    private String limitCurrency;
    private BigDecimal currentUsage;
    private BigDecimal attemptedUsage;
    private BigDecimal excessAmount;
    
    // Regulatory context
    private boolean regulatoryLimit;
    private String regulatoryAuthority; // e.g., "FinCEN", "FCA", "BaFin"
    private String regulatoryReference; // Regulation reference
    private boolean requiresReporting; // Whether this needs to be reported to authorities
    
    // User context
    private String userKycLevel; // BASIC, ENHANCED, FULL
    private String userTier; // STANDARD, PREMIUM, BUSINESS
    private Integer userRiskScore;
    private boolean userEnhancedMonitoring;
    
    // Impact assessment
    private String blockingAction; // BLOCK_TRANSFER, REQUIRE_APPROVAL, ENHANCED_VERIFICATION, SPLIT_TRANSFER
    private boolean transferBlocked;
    private boolean requiresManualApproval;
    private boolean requiresEnhancedDueDiligence;
    
    // Recipient details
    private String recipientId;
    private String recipientCountry;
    private String recipientType; // INDIVIDUAL, BUSINESS, GOVERNMENT
    
    // Compliance flags
    private boolean highRiskCountry;
    private boolean sanctionedCountry;
    private boolean structuringPattern; // Pattern of transactions designed to avoid limits
    private Integer recentCrossBorderCount; // Number of recent cross-border transfers
    
    // Resolution options
    private boolean canIncreaseLimitTemporarily;
    private boolean canSplitTransfer;
    private boolean canScheduleTransfer;
    private BigDecimal maxAllowedAmount; // Maximum that can be sent without approval
    private BigDecimal remainingLimit; // How much is left in the limit period
    
    // Additional context
    private Map<String, Object> additionalData;
    private String correlationId;
    
    @Override
    public String getEventType() {
        return "CrossBorderLimitExceededEvent";
    }
    
    @Override
    public String getAggregateId() {
        return transferId;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return additionalData;
    }

    @Override
    public String getTopic() {
        return "cross-border-limits";
    }

    @Override
    public String getAggregateType() {
        return "CrossBorderTransfer";
    }

    @Override
    public String getAggregateName() {
        return "Cross-Border Transfer";
    }

    @Override
    public Long getVersion() {
        return 1L;
    }

    @Override
    public String getSourceService() {
        return "international-transfer-service";
    }
}