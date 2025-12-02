/**
 * Suspicious Activity Report DTO
 * Represents a Suspicious Activity Report (SAR) for regulatory compliance
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
public class SuspiciousActivityReport {
    
    /**
     * SAR ID
     */
    private String sarId;
    
    /**
     * SAR reference number for regulatory filing
     */
    private String sarReferenceNumber;
    
    /**
     * User ID associated with suspicious activity
     */
    private String userId;
    
    /**
     * Customer information
     */
    private Map<String, Object> customerInfo;
    
    /**
     * Transaction IDs involved
     */
    private List<String> transactionIds;
    
    /**
     * Alert IDs that triggered this SAR
     */
    private List<String> alertIds;
    
    /**
     * Type of suspicious activity
     */
    private String activityType;
    
    /**
     * SAR category (FRAUD, MONEY_LAUNDERING, TERRORIST_FINANCING, etc.)
     */
    private String category;
    
    /**
     * SAR status (DRAFT, PENDING_REVIEW, FILED, APPROVED, REJECTED)
     */
    private String status;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String priority;
    
    /**
     * Total amount involved
     */
    private BigDecimal totalAmount;
    
    /**
     * Currency
     */
    private String currency;
    
    /**
     * Time period of suspicious activity
     */
    private Instant activityStartDate;
    
    /**
     * End of suspicious activity period
     */
    private Instant activityEndDate;
    
    /**
     * Detailed narrative of suspicious activity
     */
    private String narrative;
    
    /**
     * Reason for filing SAR
     */
    private String filingReason;
    
    /**
     * Red flags identified
     */
    private List<String> redFlags;
    
    /**
     * Supporting evidence
     */
    private List<String> supportingEvidence;
    
    /**
     * Transaction patterns observed
     */
    private Map<String, Object> transactionPatterns;
    
    /**
     * Geographic information
     */
    private Map<String, Object> geographicInfo;
    
    /**
     * Related parties involved
     */
    private List<Map<String, Object>> relatedParties;
    
    /**
     * Financial institution information
     */
    private Map<String, Object> institutionInfo;
    
    /**
     * Law enforcement contacted
     */
    private Boolean lawEnforcementContacted;
    
    /**
     * Law enforcement contact details
     */
    private Map<String, Object> lawEnforcementContact;
    
    /**
     * Regulatory authority to file with
     */
    private String regulatoryAuthority;
    
    /**
     * Filing requirements met
     */
    private List<String> filingRequirements;
    
    /**
     * Whether continuing activity
     */
    private Boolean continuingActivity;
    
    /**
     * Previous SARs filed for this customer
     */
    private List<String> previousSars;
    
    /**
     * Related SAR IDs
     */
    private List<String> relatedSars;
    
    /**
     * Investigation summary
     */
    private String investigationSummary;
    
    /**
     * Investigation officer
     */
    private String investigationOfficer;
    
    /**
     * Compliance officer who prepared SAR
     */
    private String preparedBy;
    
    /**
     * Reviewer/approver
     */
    private String reviewedBy;
    
    /**
     * Review comments
     */
    private String reviewComments;
    
    /**
     * Quality assurance checklist
     */
    private Map<String, Boolean> qaChecklist;
    
    /**
     * Filing deadline
     */
    private Instant filingDeadline;
    
    /**
     * When SAR was prepared
     */
    private Instant preparedAt;
    
    /**
     * When SAR was reviewed
     */
    private Instant reviewedAt;
    
    /**
     * When SAR was filed
     */
    private Instant filedAt;
    
    /**
     * Acknowledgment from regulator
     */
    private String regulatorAcknowledgment;
    
    /**
     * Follow-up actions required
     */
    private List<String> followUpActions;
    
    /**
     * Additional documents attached
     */
    private List<String> attachments;
    
    /**
     * SAR version number
     */
    private String version;
    
    /**
     * Amendment reason if applicable
     */
    private String amendmentReason;
    
    /**
     * Original SAR ID if this is an amendment
     */
    private String originalSarId;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * When SAR was created
     */
    private Instant createdAt;
    
    /**
     * When SAR was last updated
     */
    private Instant lastUpdatedAt;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}