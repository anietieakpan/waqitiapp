package com.waqiti.common.kyc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request for document verification in KYC process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationRequest {
    
    @NotBlank
    private String kycApplicationId;
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String customerId;
    
    @NotNull
    private DocumentType documentType;
    
    @NotBlank
    private String documentNumber;
    
    private String issuingCountry;
    private String issuingState;
    private LocalDateTime expiryDate;
    private LocalDateTime issueDate;
    
    // Customer details
    private String customerFirstName;
    private String customerLastName;
    private String customerEmail;
    private String customerPhoneNumber;
    private String customerDateOfBirth;
    private CustomerAddress customerAddress;
    
    // Document images (base64 encoded or URLs)
    private String frontImageData;
    private String backImageData;
    private String selfieImageData;
    private MultipartFile documentImage;
    private byte[] selfieImage;
    private String documentSide;
    
    // Onfido-specific fields
    private String onfidoApplicantId;
    private String onfidoDocumentId;
    private String onfidoCheckId;
    
    // Verification configuration
    private boolean enableLivenessCheck;
    private boolean enableAddressVerification;
    private boolean enableAMLCheck;
    private VerificationLevel verificationLevel;
    
    // Metadata
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private Map<String, Object> additionalData;
    
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();
    
    /**
     * Verification levels
     */
    public enum VerificationLevel {
        BASIC,          // Basic document verification
        STANDARD,       // Standard + liveness check
        ENHANCED,       // Standard + AML screening
        COMPREHENSIVE   // All checks + enhanced due diligence
    }
    
    /**
     * Check if document requires back image
     */
    public boolean requiresBackImage() {
        return documentType == DocumentType.DRIVING_LICENSE || 
               documentType == DocumentType.NATIONAL_ID;
    }
    
    /**
     * Check if liveness check is required
     */
    public boolean requiresLivenessCheck() {
        return enableLivenessCheck || 
               verificationLevel == VerificationLevel.STANDARD ||
               verificationLevel == VerificationLevel.ENHANCED ||
               verificationLevel == VerificationLevel.COMPREHENSIVE;
    }
    
    /**
     * Check if AML check is required
     */
    public boolean requiresAMLCheck() {
        return enableAMLCheck ||
               verificationLevel == VerificationLevel.ENHANCED ||
               verificationLevel == VerificationLevel.COMPREHENSIVE;
    }
}