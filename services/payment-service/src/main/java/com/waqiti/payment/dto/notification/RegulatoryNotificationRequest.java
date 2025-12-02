/**
 * Regulatory Notification Request DTO
 * Used for sending notifications to regulatory authorities following fraud containment
 */
package com.waqiti.payment.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryNotificationRequest {
    
    /**
     * Type of regulatory notification
     */
    @NotBlank(message = "Notification type is required")
    private String notificationType;
    
    /**
     * Alert ID that triggered the notification
     */
    @NotBlank(message = "Alert ID is required")
    private String alertId;
    
    /**
     * User ID associated with the incident
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Transaction ID if applicable
     */
    private String transactionId;
    
    /**
     * Type of fraud detected
     */
    @NotBlank(message = "Fraud type is required")
    private String fraudType;
    
    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Severity must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String severity;
    
    /**
     * Transaction amount involved
     */
    @DecimalMin(value = "0.0", message = "Amount must be non-negative")
    private BigDecimal transactionAmount;
    
    /**
     * Transaction currency
     */
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    /**
     * Containment actions taken
     */
    @NotEmpty(message = "Containment actions are required")
    private List<String> containmentActions;
    
    /**
     * When fraud was detected
     */
    @NotNull(message = "Detection time is required")
    private Instant detectedAt;
    
    /**
     * When containment was executed
     */
    @NotNull(message = "Execution time is required")
    private Instant executedAt;
    
    /**
     * Regulatory authority to notify (FinCEN, FCA, AUSTRAC, etc.)
     */
    @NotBlank(message = "Regulatory authority is required")
    private String regulatoryAuthority;
    
    /**
     * Jurisdiction for the notification
     */
    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;
    
    /**
     * Notification category (SUSPICIOUS_ACTIVITY, FRAUD_INCIDENT, AML_VIOLATION, etc.)
     */
    @NotBlank(message = "Notification category is required")
    private String notificationCategory;
    
    /**
     * Priority level for regulatory notification (ROUTINE, PRIORITY, URGENT, IMMEDIATE)
     */
    @Pattern(regexp = "ROUTINE|PRIORITY|URGENT|IMMEDIATE", 
             message = "Priority must be ROUTINE, PRIORITY, URGENT, or IMMEDIATE")
    private String priority;
    
    /**
     * Reporting threshold that was breached
     */
    private String reportingThreshold;
    
    /**
     * Threshold amount if applicable
     */
    private BigDecimal thresholdAmount;
    
    /**
     * Customer information for regulatory reporting
     */
    @NotNull(message = "Customer information is required")
    private Map<String, Object> customerInformation;
    
    /**
     * Transaction details for reporting
     */
    private Map<String, Object> transactionDetails;
    
    /**
     * Incident summary for regulators
     */
    @NotBlank(message = "Incident summary is required")
    @Size(min = 50, max = 2000, message = "Incident summary must be between 50 and 2000 characters")
    private String incidentSummary;
    
    /**
     * Detailed incident narrative
     */
    @Size(max = 5000, message = "Incident narrative cannot exceed 5000 characters")
    private String incidentNarrative;
    
    /**
     * Red flags or suspicious indicators
     */
    private List<String> suspiciousIndicators;
    
    /**
     * Risk factors identified
     */
    private List<String> riskFactors;
    
    /**
     * Mitigation actions taken
     */
    private List<String> mitigationActions;
    
    /**
     * Institution information
     */
    @NotNull(message = "Institution information is required")
    private Map<String, Object> institutionInformation;
    
    /**
     * Contact person for follow-up
     */
    @NotBlank(message = "Contact person is required")
    private String contactPerson;
    
    /**
     * Contact details
     */
    @NotNull(message = "Contact details are required")
    private Map<String, String> contactDetails;
    
    /**
     * Legal basis for notification
     */
    private String legalBasis;
    
    /**
     * Regulatory requirements met
     */
    private List<String> regulatoryRequirements;
    
    /**
     * Filing deadline if applicable
     */
    private Instant filingDeadline;
    
    /**
     * Whether immediate notification is required
     */
    private Boolean immediateNotificationRequired;
    
    /**
     * Whether follow-up SAR is required
     */
    private Boolean sarRequired;
    
    /**
     * Whether law enforcement notification is required
     */
    private Boolean lawEnforcementNotificationRequired;
    
    /**
     * Law enforcement contact details
     */
    private Map<String, Object> lawEnforcementContacts;
    
    /**
     * Related previous notifications
     */
    private List<String> relatedNotifications;
    
    /**
     * Supporting documentation
     */
    private List<String> supportingDocuments;
    
    /**
     * Attachments to include
     */
    private List<String> attachments;
    
    /**
     * Classification level (PUBLIC, CONFIDENTIAL, RESTRICTED)
     */
    @Pattern(regexp = "PUBLIC|CONFIDENTIAL|RESTRICTED", 
             message = "Classification must be PUBLIC, CONFIDENTIAL, or RESTRICTED")
    private String classification;
    
    /**
     * Handling instructions
     */
    private String handlingInstructions;
    
    /**
     * Reference number for tracking
     */
    private String referenceNumber;
    
    /**
     * Notification format (EMAIL, SECURE_PORTAL, FAX, PHONE)
     */
    @Pattern(regexp = "EMAIL|SECURE_PORTAL|FAX|PHONE", 
             message = "Notification format must be EMAIL, SECURE_PORTAL, FAX, or PHONE")
    private String notificationFormat;
    
    /**
     * Delivery method preferences
     */
    private Map<String, Object> deliveryPreferences;
    
    /**
     * Whether delivery confirmation is required
     */
    private Boolean requireDeliveryConfirmation;
    
    /**
     * Encryption requirements
     */
    private String encryptionRequirements;
    
    /**
     * Digital signature requirements
     */
    private Boolean digitalSignatureRequired;
    
    /**
     * Compliance officer responsible
     */
    @NotBlank(message = "Responsible compliance officer is required")
    private String responsibleComplianceOfficer;
    
    /**
     * Quality assurance reviewer
     */
    private String qaReviewer;
    
    /**
     * Review status (DRAFT, REVIEWED, APPROVED, SENT)
     */
    @Pattern(regexp = "DRAFT|REVIEWED|APPROVED|SENT", 
             message = "Review status must be DRAFT, REVIEWED, APPROVED, or SENT")
    private String reviewStatus;
    
    /**
     * Review comments
     */
    private String reviewComments;
    
    /**
     * Additional regulatory metadata
     */
    private Map<String, Object> regulatoryMetadata;
    
    /**
     * Audit trail information
     */
    private List<Map<String, Object>> auditTrail;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Request timestamp
     */
    private Instant requestTime;
    
    /**
     * Source system generating the notification
     */
    private String sourceSystem;
    
    /**
     * Requesting user/service
     */
    @NotBlank(message = "Requesting user is required")
    private String requestedBy;
}