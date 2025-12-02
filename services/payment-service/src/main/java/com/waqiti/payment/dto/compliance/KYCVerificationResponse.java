/**
 * KYC Verification Response DTO
 * Response for KYC verification operations
 */
package com.waqiti.payment.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCVerificationResponse {
    
    /**
     * Whether the operation was successful
     */
    private Boolean success;
    
    /**
     * KYC verification ID
     */
    private String kycVerificationId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * KYC verification status (PENDING, IN_PROGRESS, APPROVED, REJECTED, EXPIRED)
     */
    private String status;
    
    /**
     * KYC level verified
     */
    private String kycLevel;
    
    /**
     * Verification type
     */
    private String verificationType;
    
    /**
     * Overall verification result (PASS, FAIL, PARTIAL, PENDING)
     */
    private String verificationResult;
    
    /**
     * Verification score (0.0 to 1.0)
     */
    private Double verificationScore;
    
    /**
     * Risk level assigned (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Risk score (0.0 to 1.0)
     */
    private Double riskScore;
    
    /**
     * Identity verification result
     */
    private String identityVerificationResult;
    
    /**
     * Address verification result
     */
    private String addressVerificationResult;
    
    /**
     * Document verification results
     */
    private Map<String, String> documentVerificationResults;
    
    /**
     * Biometric verification result
     */
    private String biometricVerificationResult;
    
    /**
     * Video verification result
     */
    private String videoVerificationResult;
    
    /**
     * PEP screening result
     */
    private String pepScreeningResult;
    
    /**
     * Sanctions screening result
     */
    private String sanctionsScreeningResult;
    
    /**
     * Source of wealth verification result
     */
    private String sourceOfWealthResult;
    
    /**
     * Source of funds verification result
     */
    private String sourceOfFundsResult;
    
    /**
     * Enhanced due diligence result
     */
    private String enhancedDueDiligenceResult;
    
    /**
     * Verification methods used
     */
    private List<String> verificationMethodsUsed;
    
    /**
     * Third-party providers used
     */
    private List<String> providersUsed;
    
    /**
     * Verification flags raised
     */
    private List<String> verificationFlags;
    
    /**
     * Red flags identified
     */
    private List<String> redFlags;
    
    /**
     * Documents verified
     */
    private List<String> verifiedDocuments;
    
    /**
     * Documents rejected
     */
    private List<String> rejectedDocuments;
    
    /**
     * Missing documents
     */
    private List<String> missingDocuments;
    
    /**
     * Additional documents required
     */
    private List<String> additionalDocumentsRequired;
    
    /**
     * Verification summary
     */
    private String verificationSummary;
    
    /**
     * Verification notes
     */
    private String verificationNotes;
    
    /**
     * Rejection reason if applicable
     */
    private String rejectionReason;
    
    /**
     * Rejection details
     */
    private String rejectionDetails;
    
    /**
     * Recommended actions
     */
    private List<String> recommendedActions;
    
    /**
     * Compliance officer assigned
     */
    private String assignedComplianceOfficer;
    
    /**
     * Reviewer who processed the verification
     */
    private String reviewedBy;
    
    /**
     * Review comments
     */
    private String reviewComments;
    
    /**
     * Account restrictions recommended
     */
    private List<String> recommendedRestrictions;
    
    /**
     * Monitoring level recommended
     */
    private String recommendedMonitoringLevel;
    
    /**
     * Next review date
     */
    private Instant nextReviewDate;
    
    /**
     * KYC expiry date
     */
    private Instant expiryDate;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * When verification was initiated
     */
    private Instant initiatedAt;
    
    /**
     * When verification was completed
     */
    private Instant completedAt;
    
    /**
     * Verification audit trail
     */
    private List<Map<String, Object>> auditTrail;
    
    /**
     * Related compliance alerts created
     */
    private List<String> relatedAlerts;
    
    /**
     * Regulatory notifications triggered
     */
    private List<String> regulatoryNotifications;
    
    /**
     * Error message if operation failed
     */
    private String errorMessage;
    
    /**
     * Error code if applicable
     */
    private String errorCode;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Response version
     */
    private String version;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}