/**
 * Suspicious Activity Report Request DTO
 * Used for creating or updating Suspicious Activity Reports
 */
package com.waqiti.payment.dto.compliance;

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
public class SuspiciousActivityReportRequest {
    
    /**
     * Request action (CREATE, UPDATE, SUBMIT, AMEND)
     */
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "CREATE|UPDATE|SUBMIT|AMEND", message = "Action must be CREATE, UPDATE, SUBMIT, or AMEND")
    private String action;
    
    /**
     * Existing SAR ID for updates/amendments
     */
    private String sarId;
    
    /**
     * User ID associated with suspicious activity
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Transaction IDs involved in suspicious activity
     */
    @NotEmpty(message = "At least one transaction ID is required")
    private List<String> transactionIds;
    
    /**
     * Alert IDs that triggered this SAR
     */
    private List<String> alertIds;
    
    /**
     * Type of suspicious activity
     */
    @NotBlank(message = "Activity type is required")
    private String activityType;
    
    /**
     * SAR category
     */
    @NotBlank(message = "Category is required")
    @Pattern(regexp = "FRAUD|MONEY_LAUNDERING|TERRORIST_FINANCING|SANCTIONS_VIOLATION|OTHER", 
             message = "Category must be FRAUD, MONEY_LAUNDERING, TERRORIST_FINANCING, SANCTIONS_VIOLATION, or OTHER")
    private String category;
    
    /**
     * Priority level
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority;
    
    /**
     * Total amount involved in suspicious activity
     */
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal totalAmount;
    
    /**
     * Currency
     */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    /**
     * Start date of suspicious activity period
     */
    @NotNull(message = "Activity start date is required")
    private Instant activityStartDate;
    
    /**
     * End date of suspicious activity period
     */
    @NotNull(message = "Activity end date is required")
    private Instant activityEndDate;
    
    /**
     * Detailed narrative describing the suspicious activity
     */
    @NotBlank(message = "Narrative is required")
    @Size(min = 100, max = 5000, message = "Narrative must be between 100 and 5000 characters")
    private String narrative;
    
    /**
     * Reason for filing the SAR
     */
    @NotBlank(message = "Filing reason is required")
    private String filingReason;
    
    /**
     * Red flags identified
     */
    @NotEmpty(message = "At least one red flag must be identified")
    private List<String> redFlags;
    
    /**
     * Supporting evidence
     */
    private List<String> supportingEvidence;
    
    /**
     * Customer information
     */
    @NotNull(message = "Customer information is required")
    private Map<String, Object> customerInfo;
    
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
     * Whether law enforcement has been contacted
     */
    private Boolean lawEnforcementContacted;
    
    /**
     * Law enforcement contact details
     */
    private Map<String, Object> lawEnforcementContact;
    
    /**
     * Regulatory authority to file with
     */
    @NotBlank(message = "Regulatory authority is required")
    private String regulatoryAuthority;
    
    /**
     * Whether this is continuing activity
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
     * Investigation officer assigned
     */
    private String investigationOfficer;
    
    /**
     * Compliance officer preparing the SAR
     */
    @NotBlank(message = "Prepared by is required")
    private String preparedBy;
    
    /**
     * Reviewer/approver for the SAR
     */
    private String reviewedBy;
    
    /**
     * Review comments
     */
    private String reviewComments;
    
    /**
     * Filing deadline
     */
    private Instant filingDeadline;
    
    /**
     * Priority justification if HIGH or CRITICAL
     */
    private String priorityJustification;
    
    /**
     * Amendment reason (required if action is AMEND)
     */
    private String amendmentReason;
    
    /**
     * Additional documents to attach
     */
    private List<String> attachments;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Request timestamp
     */
    private Instant requestTime;
}