/**
 * Compliance Alert DTO
 * Represents a compliance alert entity
 */
package com.waqiti.payment.dto.compliance;

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
public class ComplianceAlert {
    
    /**
     * Compliance alert ID
     */
    private String complianceAlertId;
    
    /**
     * Original alert ID that triggered this compliance alert
     */
    private String originalAlertId;
    
    /**
     * User ID associated with the alert
     */
    private String userId;
    
    /**
     * Transaction ID if applicable
     */
    private String transactionId;
    
    /**
     * Alert type (FRAUD_CONTAINMENT_EXECUTED, AML_VIOLATION, SANCTIONS_HIT, etc.)
     */
    private String alertType;
    
    /**
     * Alert category
     */
    private String alertCategory;
    
    /**
     * Fraud type that triggered the alert
     */
    private String fraudType;
    
    /**
     * Current status (OPEN, INVESTIGATING, PENDING_REVIEW, RESOLVED, CLOSED)
     */
    private String status;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String priority;
    
    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String severity;
    
    /**
     * Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Fraud score (0.0 to 1.0)
     */
    private Double fraudScore;
    
    /**
     * Case number assigned
     */
    private String caseNumber;
    
    /**
     * Reference number for external tracking
     */
    private String referenceNumber;
    
    /**
     * Alert title/summary
     */
    private String title;
    
    /**
     * Detailed description
     */
    private String description;
    
    /**
     * Reason for the alert
     */
    private String reason;
    
    /**
     * Containment actions taken
     */
    private List<String> containmentActions;
    
    /**
     * Containment reason
     */
    private String containmentReason;
    
    /**
     * Affected accounts
     */
    private List<String> affectedAccounts;
    
    /**
     * Affected cards
     */
    private List<String> affectedCards;
    
    /**
     * Affected transactions
     */
    private List<String> affectedTransactions;
    
    /**
     * Transaction amount if applicable
     */
    private BigDecimal transactionAmount;
    
    /**
     * Transaction currency
     */
    private String currency;
    
    /**
     * Account status
     */
    private String accountStatus;
    
    /**
     * Whether regulator notification is required
     */
    private Boolean requiresRegulatorNotification;
    
    /**
     * Whether regulator has been notified
     */
    private Boolean regulatorNotified;
    
    /**
     * When regulator was notified
     */
    private Instant regulatorNotifiedAt;
    
    /**
     * Whether investigation is required
     */
    private Boolean requiresInvestigation;
    
    /**
     * Investigation status
     */
    private String investigationStatus;
    
    /**
     * Investigation findings
     */
    private String investigationFindings;
    
    /**
     * Team assigned to handle the alert
     */
    private String assignedTeam;
    
    /**
     * Individual assignee
     */
    private String assignedTo;
    
    /**
     * Resolution details
     */
    private String resolution;
    
    /**
     * Resolution reason
     */
    private String resolutionReason;
    
    /**
     * Actions taken to resolve
     */
    private List<String> resolutionActions;
    
    /**
     * Whether this is a false positive
     */
    private Boolean falsePositive;
    
    /**
     * Investigation notes
     */
    private String investigationNotes;
    
    /**
     * Compliance notes
     */
    private String complianceNotes;
    
    /**
     * Related compliance alerts
     */
    private List<String> relatedAlerts;
    
    /**
     * Regulatory requirements triggered
     */
    private List<String> regulatoryRequirements;
    
    /**
     * Escalation level
     */
    private String escalationLevel;
    
    /**
     * SLA deadline
     */
    private Instant slaDeadline;
    
    /**
     * When fraud was detected
     */
    private Instant detectedAt;
    
    /**
     * When containment was executed
     */
    private Instant executedAt;
    
    /**
     * Who executed the containment
     */
    private String executedBy;
    
    /**
     * Source of execution
     */
    private String executionSource;
    
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
     * Additional metadata
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