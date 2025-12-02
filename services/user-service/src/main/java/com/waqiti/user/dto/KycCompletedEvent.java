package com.waqiti.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * KYC completion event - CRITICAL for user onboarding flow.
 * This event is produced by kyc-service after successful verification
 * and MUST be consumed to activate user accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KycCompletedEvent {
    
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
    
    // ======================== KYC Verification Details ========================
    @NotBlank(message = "KYC verification ID is required")
    private String kycVerificationId;
    
    @NotNull(message = "Verification level is required")
    private VerificationLevel verificationLevel;
    
    @NotNull(message = "Verification status is required")
    private VerificationStatus verificationStatus;
    
    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double verificationScore;
    
    @NotNull
    private LocalDateTime verificationCompletedAt;
    
    private LocalDateTime verificationStartedAt;
    
    // ======================== Personal Information ========================
    @NotBlank
    private String firstName;
    
    @NotBlank
    private String lastName;
    
    private String middleName;
    
    @NotNull
    private LocalDateTime dateOfBirth;
    
    @NotBlank
    private String nationality;
    
    private String countryOfResidence;
    
    // ======================== Document Verification ========================
    @NotNull
    private DocumentType primaryDocumentType;
    
    @NotBlank
    private String primaryDocumentNumber;
    
    private String primaryDocumentCountry;
    
    private LocalDateTime primaryDocumentExpiry;
    
    private Boolean primaryDocumentVerified;
    
    private DocumentType secondaryDocumentType;
    
    private String secondaryDocumentNumber;
    
    private Boolean secondaryDocumentVerified;
    
    // ======================== Address Verification ========================
    @NotBlank
    private String addressLine1;
    
    private String addressLine2;
    
    @NotBlank
    private String city;
    
    private String state;
    
    @NotBlank
    private String postalCode;
    
    @NotBlank
    private String country;
    
    private Boolean addressVerified;
    
    private String addressVerificationMethod;
    
    // ======================== Biometric Verification ========================
    private Boolean biometricVerificationCompleted;
    
    private String biometricType;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double biometricMatchScore;
    
    private Boolean livenessCheckPassed;
    
    // ======================== Risk Assessment ========================
    @NotNull
    private RiskLevel riskLevel;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double riskScore;
    
    private List<String> riskFactors;
    
    private Map<String, Double> riskScoreBreakdown;
    
    // ======================== Compliance Checks ========================
    private Boolean amlCheckPassed;
    
    private Boolean sanctionsCheckPassed;
    
    private Boolean pepCheckPassed;
    
    private Boolean adverseMediaCheckPassed;
    
    private List<String> complianceFlags;
    
    private String complianceNotes;
    
    // ======================== Account Limits & Permissions ========================
    @NotNull
    private AccountTier accountTier;
    
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal dailyTransactionLimit;
    
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal monthlyTransactionLimit;
    
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal singleTransactionLimit;
    
    private List<String> enabledFeatures;
    
    private List<String> restrictedFeatures;
    
    private Map<String, Boolean> permissions;
    
    // ======================== Verification Provider Details ========================
    private String verificationProvider;
    
    private String providerSessionId;
    
    private String providerReferenceId;
    
    private Map<String, Object> providerMetadata;
    
    // ======================== Review & Approval ========================
    private Boolean manualReviewRequired;
    
    private Boolean manualReviewCompleted;
    
    private String reviewedBy;
    
    private LocalDateTime reviewedAt;
    
    private String reviewNotes;
    
    private Boolean approved;
    
    private String approvalNotes;
    
    // ======================== Next Steps & Actions ========================
    @NotNull
    @Builder.Default
    private Boolean activateAccount = true;
    
    @Builder.Default
    private Boolean sendWelcomeEmail = true;
    
    @Builder.Default
    private Boolean enableTransactions = true;
    
    @Builder.Default
    private Boolean requireAdditionalVerification = false;
    
    private List<String> additionalVerificationRequired;
    
    private LocalDateTime nextReviewDate;
    
    // ======================== Metadata ========================
    private Map<String, Object> additionalData;
    
    private String sessionId;
    
    private String deviceId;
    
    private String ipAddress;
    
    private String userAgent;
    
    // ======================== Enums ========================
    
    public enum VerificationLevel {
        BASIC("Basic identity verification"),
        STANDARD("Standard KYC verification"),
        ENHANCED("Enhanced due diligence"),
        PREMIUM("Premium verification with biometrics");
        
        private final String description;
        
        VerificationLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum VerificationStatus {
        COMPLETED("Verification completed successfully"),
        COMPLETED_WITH_CONDITIONS("Completed with conditions"),
        PENDING_REVIEW("Pending manual review"),
        PARTIALLY_COMPLETED("Partially completed");
        
        private final String description;
        
        VerificationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum DocumentType {
        PASSPORT,
        DRIVERS_LICENSE,
        NATIONAL_ID,
        RESIDENCE_PERMIT,
        UTILITY_BILL,
        BANK_STATEMENT,
        TAX_DOCUMENT,
        OTHER
    }
    
    public enum RiskLevel {
        LOW("Low risk - standard monitoring"),
        MEDIUM("Medium risk - enhanced monitoring"),
        HIGH("High risk - frequent reviews"),
        VERY_HIGH("Very high risk - restricted features");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum AccountTier {
        STARTER("Basic account with limited features"),
        STANDARD("Standard account with regular limits"),
        PREMIUM("Premium account with higher limits"),
        BUSINESS("Business account with commercial features"),
        ENTERPRISE("Enterprise account with custom limits");
        
        private final String description;
        
        AccountTier(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // ======================== Helper Methods ========================
    
    public boolean requiresManualIntervention() {
        return Boolean.TRUE.equals(manualReviewRequired) && 
               !Boolean.TRUE.equals(manualReviewCompleted);
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.VERY_HIGH;
    }
    
    public boolean hasComplianceIssues() {
        return !Boolean.TRUE.equals(amlCheckPassed) ||
               !Boolean.TRUE.equals(sanctionsCheckPassed) ||
               !Boolean.TRUE.equals(pepCheckPassed) ||
               (complianceFlags != null && !complianceFlags.isEmpty());
    }
    
    public boolean canActivateImmediately() {
        return Boolean.TRUE.equals(activateAccount) && 
               !requiresManualIntervention() &&
               !Boolean.TRUE.equals(requireAdditionalVerification);
    }
}