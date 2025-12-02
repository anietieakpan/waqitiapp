/**
 * Risk Alert DTO
 * Represents a risk alert or fraud alert
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAlert {
    
    /**
     * Alert ID
     */
    private String alertId;
    
    /**
     * User ID associated with the alert
     */
    private String userId;
    
    /**
     * Transaction ID if applicable
     */
    private String transactionId;
    
    /**
     * Alert type (FRAUD, AML, SANCTIONS, BEHAVIORAL, etc.)
     */
    private String alertType;
    
    /**
     * Alert category
     */
    private String alertCategory;
    
    /**
     * Alert severity (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String severity;
    
    /**
     * Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Risk score associated with alert (0.0 to 1.0)
     */
    private Double riskScore;
    
    /**
     * Fraud score if applicable (0.0 to 1.0)
     */
    private Double fraudScore;
    
    /**
     * Alert status (OPEN, INVESTIGATING, RESOLVED, CLOSED, FALSE_POSITIVE)
     */
    private String status;
    
    /**
     * Alert priority (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String priority;
    
    /**
     * Alert title/summary
     */
    private String title;
    
    /**
     * Detailed alert description
     */
    private String description;
    
    /**
     * Reason for the alert
     */
    private String reason;
    
    /**
     * Rules that triggered the alert
     */
    private List<String> triggeredRules;
    
    /**
     * Risk factors detected
     */
    private List<String> riskFactors;
    
    /**
     * Fraud indicators found
     */
    private List<String> fraudIndicators;
    
    /**
     * Transaction amount if applicable
     */
    private BigDecimal transactionAmount;
    
    /**
     * Transaction currency if applicable
     */
    private String transactionCurrency;
    
    /**
     * Payment method involved
     */
    private String paymentMethod;
    
    /**
     * Device information
     */
    private Map<String, Object> deviceInfo;
    
    /**
     * Geographic information
     */
    private Map<String, Object> geographicInfo;
    
    /**
     * Alert source (SYSTEM, MANUAL, EXTERNAL)
     */
    private String alertSource;
    
    /**
     * Source system that generated the alert
     */
    private String sourceSystem;
    
    /**
     * Model version used for detection
     */
    private String modelVersion;
    
    /**
     * Confidence level of the alert (0.0 to 1.0)
     */
    private Double confidenceLevel;
    
    /**
     * Whether alert requires immediate action
     */
    private Boolean requiresImmediateAction;
    
    /**
     * Whether manual review is required
     */
    private Boolean requiresManualReview;
    
    /**
     * Recommended actions
     */
    private List<String> recommendedActions;
    
    /**
     * Actions taken in response to alert
     */
    private List<String> actionsTaken;
    
    /**
     * Alert assignee
     */
    private String assignedTo;
    
    /**
     * Team responsible for handling
     */
    private String assignedTeam;
    
    /**
     * Resolution details if resolved
     */
    private String resolution;
    
    /**
     * Resolution reason
     */
    private String resolutionReason;
    
    /**
     * Whether this is a false positive
     */
    private Boolean falsePositive;
    
    /**
     * Investigation notes
     */
    private String investigationNotes;
    
    /**
     * Related alerts
     */
    private List<String> relatedAlerts;
    
    /**
     * Escalation level
     */
    private String escalationLevel;
    
    /**
     * SLA deadline
     */
    private Instant slaDeadline;
    
    /**
     * When alert was created
     */
    private Instant createdAt;
    
    /**
     * When alert was last updated
     */
    private Instant lastUpdatedAt;
    
    /**
     * When alert was resolved
     */
    private Instant resolvedAt;
    
    /**
     * Alert expiry time
     */
    private Instant expiresAt;
    
    /**
     * Additional alert metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Alert version for tracking changes
     */
    private String version;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}