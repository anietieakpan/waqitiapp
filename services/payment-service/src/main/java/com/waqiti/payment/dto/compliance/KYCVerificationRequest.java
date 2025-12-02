/**
 * KYC Verification Request DTO
 * Used for initiating or updating KYC verification processes
 */
package com.waqiti.payment.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCVerificationRequest {
    
    /**
     * Request action (INITIATE, UPDATE, REVIEW, APPROVE, REJECT)
     */
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "INITIATE|UPDATE|REVIEW|APPROVE|REJECT", 
             message = "Action must be INITIATE, UPDATE, REVIEW, APPROVE, or REJECT")
    private String action;
    
    /**
     * User ID for KYC verification
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * KYC verification ID (for updates)
     */
    private String kycVerificationId;
    
    /**
     * KYC level required (BASIC, STANDARD, ENHANCED)
     */
    @NotBlank(message = "KYC level is required")
    @Pattern(regexp = "BASIC|STANDARD|ENHANCED", message = "KYC level must be BASIC, STANDARD, or ENHANCED")
    private String kycLevel;
    
    /**
     * Verification type (INDIVIDUAL, BUSINESS, TRUST, OTHER)
     */
    @NotBlank(message = "Verification type is required")
    @Pattern(regexp = "INDIVIDUAL|BUSINESS|TRUST|OTHER", 
             message = "Verification type must be INDIVIDUAL, BUSINESS, TRUST, or OTHER")
    private String verificationType;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, URGENT)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "Priority must be LOW, MEDIUM, HIGH, or URGENT")
    private String priority;
    
    /**
     * Customer information for verification
     */
    @NotNull(message = "Customer information is required")
    private Map<String, Object> customerInfo;
    
    /**
     * Identity documents submitted
     */
    private List<Map<String, Object>> identityDocuments;
    
    /**
     * Address documents submitted
     */
    private List<Map<String, Object>> addressDocuments;
    
    /**
     * Financial documents submitted
     */
    private List<Map<String, Object>> financialDocuments;
    
    /**
     * Additional documents submitted
     */
    private List<Map<String, Object>> additionalDocuments;
    
    /**
     * Document verification method (MANUAL, AUTOMATED, HYBRID)
     */
    @Pattern(regexp = "MANUAL|AUTOMATED|HYBRID", 
             message = "Verification method must be MANUAL, AUTOMATED, or HYBRID")
    private String verificationMethod;
    
    /**
     * Third-party verification providers to use
     */
    private List<String> verificationProviders;
    
    /**
     * Biometric verification required
     */
    private Boolean biometricVerificationRequired;
    
    /**
     * Video verification required
     */
    private Boolean videoVerificationRequired;
    
    /**
     * Enhanced due diligence required
     */
    private Boolean enhancedDueDiligenceRequired;
    
    /**
     * PEP screening required
     */
    private Boolean pepScreeningRequired;
    
    /**
     * Sanctions screening required
     */
    private Boolean sanctionsScreeningRequired;
    
    /**
     * Source of wealth verification required
     */
    private Boolean sourceOfWealthRequired;
    
    /**
     * Source of funds verification required
     */
    private Boolean sourceOfFundsRequired;
    
    /**
     * Occupation verification required
     */
    private Boolean occupationVerificationRequired;
    
    /**
     * Business verification details (for business accounts)
     */
    private Map<String, Object> businessVerificationDetails;
    
    /**
     * Ultimate beneficial owners (for business accounts)
     */
    private List<Map<String, Object>> ultimateBeneficialOwners;
    
    /**
     * Risk assessment parameters
     */
    private Map<String, Object> riskAssessmentParams;
    
    /**
     * Compliance officer assigned
     */
    private String assignedComplianceOfficer;
    
    /**
     * Review deadline
     */
    private Instant reviewDeadline;
    
    /**
     * Special instructions
     */
    private String specialInstructions;
    
    /**
     * Reason for verification (NEW_ACCOUNT, PERIODIC_REVIEW, RISK_EVENT, REGULATORY_REQUIREMENT)
     */
    @Pattern(regexp = "NEW_ACCOUNT|PERIODIC_REVIEW|RISK_EVENT|REGULATORY_REQUIREMENT", 
             message = "Reason must be NEW_ACCOUNT, PERIODIC_REVIEW, RISK_EVENT, or REGULATORY_REQUIREMENT")
    private String verificationReason;
    
    /**
     * Alert ID that triggered verification (if applicable)
     */
    private String triggeringAlertId;
    
    /**
     * Previous KYC verification ID (for updates/renewals)
     */
    private String previousKycId;
    
    /**
     * Rejection reason (for REJECT action)
     */
    private String rejectionReason;
    
    /**
     * Rejection comments (for REJECT action)
     */
    private String rejectionComments;
    
    /**
     * Approval comments (for APPROVE action)
     */
    private String approvalComments;
    
    /**
     * Review comments
     */
    private String reviewComments;
    
    /**
     * Requested by (user ID or system)
     */
    @NotBlank(message = "Requested by is required")
    private String requestedBy;
    
    /**
     * Request source (CUSTOMER, SYSTEM, COMPLIANCE_OFFICER, REGULATOR)
     */
    @Pattern(regexp = "CUSTOMER|SYSTEM|COMPLIANCE_OFFICER|REGULATOR", 
             message = "Request source must be CUSTOMER, SYSTEM, COMPLIANCE_OFFICER, or REGULATOR")
    private String requestSource;
    
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