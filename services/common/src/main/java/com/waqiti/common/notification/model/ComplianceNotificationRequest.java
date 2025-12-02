package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Compliance notification request
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ComplianceNotificationRequest extends NotificationRequest {
    
    /**
     * Type of compliance notification
     */
    private ComplianceType complianceType;
    
    /**
     * Regulation or standard
     */
    private String regulation;
    
    /**
     * Notification title
     */
    private String title;
    
    /**
     * Detailed message
     */
    private String message;
    
    /**
     * Required actions
     */
    private List<Map<String, Object>> requiredActions;
    
    /**
     * Deadline for compliance
     */
    private Instant deadline;
    
    /**
     * Affected data categories
     */
    private List<String> affectedDataCategories;
    
    /**
     * Compliance status
     */
    private ComplianceStatus status;
    
    /**
     * Risk level
     */
    private RiskLevel riskLevel;
    
    /**
     * Legal jurisdiction
     */
    private String jurisdiction;
    
    /**
     * Reference documents
     */
    private List<Map<String, Object>> documents;
    
    /**
     * Audit trail reference
     */
    private String auditTrailId;
    
    /**
     * Penalties for non-compliance
     */
    private Map<String, Object> penaltyInfo;
    
    /**
     * Contact information
     */
    private Map<String, Object> complianceContact;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    
    public enum ComplianceType {
        DATA_PRIVACY,
        FINANCIAL_REPORTING,
        ANTI_MONEY_LAUNDERING,
        KNOW_YOUR_CUSTOMER,
        DATA_RETENTION,
        DATA_DELETION,
        CONSENT_MANAGEMENT,
        BREACH_NOTIFICATION,
        AUDIT_REQUIREMENT,
        REGULATORY_CHANGE,
        LICENSE_RENEWAL,
        CERTIFICATION_UPDATE,
        POLICY_UPDATE,
        TRAINING_REQUIRED,
        ASSESSMENT_DUE,
        REMEDIATION_REQUIRED
    }
    
    public enum ComplianceStatus {
        COMPLIANT,
        NON_COMPLIANT,
        PARTIALLY_COMPLIANT,
        UNDER_REVIEW,
        REMEDIATION_IN_PROGRESS,
        EXEMPTED,
        FAILED,
        COMPLETED,
        SUBMITTED
    }
    
    public enum RiskLevel {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
    
    public enum ActionType {
        DOCUMENTATION,
        SYSTEM_CHANGE,
        PROCESS_CHANGE,
        TRAINING,
        AUDIT,
        CERTIFICATION,
        NOTIFICATION,
        REMEDIATION
    }
    
    public enum DocumentType {
        REGULATION,
        POLICY,
        PROCEDURE,
        EVIDENCE,
        REPORT,
        CERTIFICATE,
        AUDIT_REPORT,
        GUIDANCE
    }
}