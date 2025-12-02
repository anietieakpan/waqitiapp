package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * Document Data DTO for KYC document processing
 * Contains structured data extracted from documents
 * 
 * @author Waqiti KYC Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentData {
    
    // Basic document information
    private String documentType;
    private String documentNumber;
    private String rawText;
    private double confidenceScore;
    
    // Personal information
    private String fullName;
    private String firstName;
    private String lastName;
    private String middleName;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    
    // Document specific fields
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String issuingAuthority;
    private String placeOfBirth;
    private String licenseClass; // For driver's license
    
    // Address information
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    // Status flags
    private boolean verified;
    private boolean expired;
    private boolean expiringSoon;
    private boolean requiresManualReview;
    private String reviewReason;
    
    // Security and validation
    private boolean passedSecurityCheck;
    private boolean passedQualityCheck;
    private Map<String, Object> securityFeatures;
    private Map<String, Object> additionalData;
    
    // Verification details
    private String verificationId;
    private String providerId;
    private String verificationMethod;
    
    public boolean isValid() {
        return verified && !expired && passedSecurityCheck && passedQualityCheck;
    }
    
    public boolean needsReview() {
        return requiresManualReview || confidenceScore < 80.0 || expiringSoon;
    }
}