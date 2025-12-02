package com.waqiti.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * KYC rejection event - handles failed KYC verifications.
 * This event is produced by kyc-service when verification fails
 * and must be consumed to update user status and notify them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KycRejectedEvent {
    
    // ======================== Event Metadata ========================
    @NotBlank(message = "Event ID is required")
    private String eventId;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @NotBlank(message = "Source service is required")
    private String source;
    
    // ======================== User Identification ========================
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Account ID is required")
    private String accountId;
    
    @Email(message = "Valid email is required")
    @NotBlank
    private String email;
    
    private String phoneNumber;
    
    private String firstName;
    
    private String lastName;
    
    // ======================== KYC Verification Details ========================
    @NotBlank(message = "KYC verification ID is required")
    private String kycVerificationId;
    
    @NotNull(message = "Rejection reason is required")
    private RejectionReason rejectionReason;
    
    @NotBlank(message = "Rejection details are required")
    @Size(max = 2000)
    private String rejectionDetails;
    
    private List<String> failedChecks;
    
    private Map<String, String> verificationResults;
    
    @NotNull
    private LocalDateTime verificationAttemptedAt;
    
    private LocalDateTime rejectedAt;
    
    private Integer attemptNumber;
    
    private Integer maxAttempts;
    
    // ======================== Document Issues ========================
    private Boolean documentIssues;
    
    private List<DocumentIssue> documentIssuesList;
    
    private String primaryDocumentStatus;
    
    private String secondaryDocumentStatus;
    
    private List<String> documentRejectionReasons;
    
    // ======================== Identity Verification Issues ========================
    private Boolean identityMismatch;
    
    private List<String> mismatchedFields;
    
    private Double identityMatchScore;
    
    private Boolean biometricFailed;
    
    private String biometricFailureReason;
    
    private Boolean livenessCheckFailed;
    
    // ======================== Compliance Issues ========================
    private Boolean amlFailed;
    
    private Boolean sanctionsFailed;
    
    private Boolean pepFailed;
    
    private Boolean adverseMediaFound;
    
    private List<String> complianceFailures;
    
    private String complianceNotes;
    
    // ======================== Risk Assessment ========================
    @NotNull
    private RiskLevel riskLevel;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double riskScore;
    
    private List<String> riskFactors;
    
    private Boolean highRiskJurisdiction;
    
    // ======================== Retry Information ========================
    @NotNull
    @Builder.Default
    private Boolean canRetry = false;
    
    private LocalDateTime retryAfter;
    
    private List<String> requiredActions;
    
    private List<String> requiredDocuments;
    
    private String retryInstructions;
    
    // ======================== Manual Review ========================
    private Boolean manualReviewPerformed;
    
    private String reviewedBy;
    
    private LocalDateTime reviewedAt;
    
    private String reviewerNotes;
    
    private String reviewDecision;
    
    // ======================== Provider Information ========================
    private String verificationProvider;
    
    private String providerReferenceId;
    
    private String providerErrorCode;
    
    private String providerErrorMessage;
    
    private Map<String, Object> providerResponse;
    
    // ======================== User Actions ========================
    @NotNull
    @Builder.Default
    private Boolean suspendAccount = true;
    
    @NotNull
    @Builder.Default
    private Boolean notifyUser = true;
    
    @Builder.Default
    private Boolean blockTransactions = true;
    
    @Builder.Default
    private Boolean allowAppeal = true;
    
    private LocalDateTime appealDeadline;
    
    // ======================== Support Information ========================
    private String supportTicketId;
    
    private String supportContactEmail;
    
    private String supportContactPhone;
    
    private List<String> supportInstructions;
    
    // ======================== Metadata ========================
    private Map<String, Object> additionalData;
    
    private String sessionId;
    
    private String deviceId;
    
    private String ipAddress;
    
    // ======================== Enums ========================
    
    public enum RejectionReason {
        // Document issues
        INVALID_DOCUMENT("Invalid or unreadable document"),
        EXPIRED_DOCUMENT("Document has expired"),
        DOCUMENT_MISMATCH("Document information doesn't match"),
        UNSUPPORTED_DOCUMENT("Document type not supported"),
        FRAUDULENT_DOCUMENT("Document appears fraudulent"),
        
        // Identity issues
        IDENTITY_NOT_VERIFIED("Identity could not be verified"),
        NAME_MISMATCH("Name doesn't match documents"),
        DOB_MISMATCH("Date of birth doesn't match"),
        ADDRESS_MISMATCH("Address verification failed"),
        SELFIE_MISMATCH("Selfie doesn't match document"),
        
        // Biometric issues
        BIOMETRIC_FAILURE("Biometric verification failed"),
        LIVENESS_CHECK_FAILED("Liveness detection failed"),
        FACE_MATCH_FAILED("Face matching failed"),
        
        // Compliance issues
        AML_FAILURE("Anti-money laundering check failed"),
        SANCTIONS_HIT("Sanctions list match found"),
        PEP_MATCH("Politically exposed person"),
        ADVERSE_MEDIA("Adverse media found"),
        HIGH_RISK("Risk level too high"),
        
        // Age/eligibility
        UNDERAGE("User is under minimum age"),
        RESTRICTED_JURISDICTION("Jurisdiction not supported"),
        INELIGIBLE("User is ineligible for account"),
        
        // Technical issues
        INCOMPLETE_VERIFICATION("Verification process incomplete"),
        TIMEOUT("Verification session timed out"),
        TECHNICAL_ERROR("Technical error during verification"),
        
        // Manual review
        MANUAL_REJECTION("Manually rejected by reviewer"),
        SUSPICIOUS_ACTIVITY("Suspicious activity detected"),
        DUPLICATE_ACCOUNT("Duplicate account detected"),
        
        // Other
        TERMS_VIOLATION("Terms of service violation"),
        FRAUD_SUSPECTED("Fraud suspected"),
        OTHER("Other reason");
        
        private final String description;
        
        RejectionReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum DocumentIssue {
        BLURRY("Document image is blurry"),
        PARTIALLY_VISIBLE("Document not fully visible"),
        GLARE("Too much glare on document"),
        DARK("Image too dark"),
        OVEREXPOSED("Image overexposed"),
        WRONG_SIDE("Wrong side of document"),
        MULTIPLE_DOCUMENTS("Multiple documents in image"),
        NO_DOCUMENT("No document found in image"),
        TAMPERED("Document appears tampered"),
        PHOTOCOPIED("Photocopy not accepted"),
        SCREENSHOT("Screenshot not accepted"),
        BLACK_WHITE("Color image required");
        
        private final String description;
        
        DocumentIssue(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum RiskLevel {
        LOW("Low risk"),
        MEDIUM("Medium risk"),
        HIGH("High risk"),
        VERY_HIGH("Very high risk"),
        UNACCEPTABLE("Unacceptable risk");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // ======================== Helper Methods ========================
    
    public boolean isComplianceRejection() {
        return rejectionReason == RejectionReason.AML_FAILURE ||
               rejectionReason == RejectionReason.SANCTIONS_HIT ||
               rejectionReason == RejectionReason.PEP_MATCH ||
               rejectionReason == RejectionReason.ADVERSE_MEDIA ||
               rejectionReason == RejectionReason.HIGH_RISK;
    }
    
    public boolean isDocumentRejection() {
        return rejectionReason == RejectionReason.INVALID_DOCUMENT ||
               rejectionReason == RejectionReason.EXPIRED_DOCUMENT ||
               rejectionReason == RejectionReason.DOCUMENT_MISMATCH ||
               rejectionReason == RejectionReason.UNSUPPORTED_DOCUMENT ||
               rejectionReason == RejectionReason.FRAUDULENT_DOCUMENT;
    }
    
    public boolean isIdentityRejection() {
        return rejectionReason == RejectionReason.IDENTITY_NOT_VERIFIED ||
               rejectionReason == RejectionReason.NAME_MISMATCH ||
               rejectionReason == RejectionReason.DOB_MISMATCH ||
               rejectionReason == RejectionReason.ADDRESS_MISMATCH ||
               rejectionReason == RejectionReason.SELFIE_MISMATCH;
    }
    
    public boolean isPermanentRejection() {
        return rejectionReason == RejectionReason.UNDERAGE ||
               rejectionReason == RejectionReason.RESTRICTED_JURISDICTION ||
               rejectionReason == RejectionReason.FRAUD_SUSPECTED ||
               rejectionReason == RejectionReason.DUPLICATE_ACCOUNT ||
               (isComplianceRejection() && !Boolean.TRUE.equals(allowAppeal));
    }
    
    public boolean hasReachedMaxAttempts() {
        return attemptNumber != null && maxAttempts != null && 
               attemptNumber >= maxAttempts;
    }
}