package com.waqiti.compliance.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Compliance domain event model for AML reports, OFAC sanctions screening, KYC verification,
 * PEP screening, trade-based money laundering, whistleblower reports, regulatory examinations,
 * compliance audits, Reg E compliance, and UCC filing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceDomainEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String complianceId;
    private String reportId;
    private String customerId;
    private String userId;
    private String transactionId;
    private String entityId;
    private String reportType;
    private String screeningType;
    private String verificationStatus;
    private String complianceStatus;
    private BigDecimal amount;
    private String currency;
    private String riskLevel;
    private String riskScore;
    private String matchStatus;
    private String sanctionsList;
    private String pepStatus;
    private String pepCategory;
    private String kycTier;
    private String kycDocumentType;
    private String documentStatus;
    private String pepRiskRating;
    private String moneyLaunderingType;
    private String tradeFinanceType;
    private String suspiciousActivity;
    private String whistleblowerCategory;
    private String reportingChannel;
    private String examinationType;
    private String regulatoryBody;
    private String auditType;
    private String auditScope;
    private String auditResult;
    private String regulationName;
    private String complianceFramework;
    private String filingType;
    private String filingStatus;
    private String filingReference;
    private Instant filingDate;
    private Instant expiryDate;
    private Instant reviewDate;
    private String severity;
    private String resolution;
    private String investigationId;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String status;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is an AML report event
     */
    public boolean isAMLReportEvent() {
        return "AML_REPORT".equals(eventType);
    }
    
    /**
     * Check if this is a high-risk event
     */
    public boolean isHighRiskEvent() {
        return "HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(severity);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "REGULATORY_EXAMINATION".equals(eventType) || 
               "WHISTLEBLOWER_REPORT".equals(eventType) ||
               isHighRiskEvent();
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}