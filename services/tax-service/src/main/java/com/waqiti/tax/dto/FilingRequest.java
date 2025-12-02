package com.waqiti.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilingRequest {
    
    @NotNull(message = "Electronic signature is required")
    private Boolean agreeToElectronicSignature;
    
    @NotBlank(message = "Signature PIN is required")
    private String signaturePin;
    
    @NotNull(message = "Terms acceptance is required")
    private Boolean acceptTermsAndConditions;
    
    @NotNull(message = "Accuracy declaration is required")
    private Boolean declareAccuracy;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String deviceFingerprint;
    
    private Boolean authorizeDirectDeposit;
    
    private String bankAccountType; // CHECKING, SAVINGS
    
    private String routingNumber;
    
    private String accountNumber;
    
    private Boolean splitRefund;
    
    private String spouseSignaturePin; // For married filing jointly
    
    private Boolean spouseAgreeToElectronicSignature;
    
    private String preparerPin; // For paid preparers
    
    private String preparerId;
    
    private String additionalNotes;
}