package com.waqiti.risk.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Risk domain event model for credit risk assessment, operational risk, liquidity risk,
 * counterparty risk, concentration risk, model risk validation, stress testing, and risk appetite monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String riskId;
    private String assessmentId;
    private String userId;
    private String entityId;
    private String riskType;
    private String riskCategory;
    private BigDecimal riskScore;
    private BigDecimal previousRiskScore;
    private String riskRating;
    private String severity;
    private BigDecimal probability;
    private BigDecimal impact;
    private BigDecimal exposure;
    private String creditScore;
    private BigDecimal creditLimit;
    private BigDecimal outstandingAmount;
    private String operationalRiskType;
    private String riskIndicator;
    private BigDecimal liquidityRatio;
    private BigDecimal capitalRatio;
    private String counterpartyId;
    private String counterpartyType;
    private String counterpartyRating;
    private BigDecimal concentrationLimit;
    private BigDecimal concentrationRatio;
    private String modelId;
    private String modelVersion;
    private String validationStatus;
    private String stressTestScenario;
    private BigDecimal stressTestResult;
    private String riskAppetiteMetric;
    private BigDecimal riskAppetiteLimit;
    private BigDecimal riskAppetiteUtilization;
    private String mitigationAction;
    private String complianceStatus;
    private String regulatoryFramework;
    private String currency;
    private Instant assessmentDate;
    private Instant reviewDate;
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
     * Check if this is a credit risk event
     */
    public boolean isCreditRiskEvent() {
        return "CREDIT_RISK_ASSESSMENT".equals(eventType);
    }
    
    /**
     * Check if this is a high-risk event
     */
    public boolean isHighRiskEvent() {
        return riskScore != null && riskScore.compareTo(new BigDecimal("8.0")) > 0;
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "OPERATIONAL_RISK".equals(eventType) || 
               "STRESS_TESTING".equals(eventType) ||
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