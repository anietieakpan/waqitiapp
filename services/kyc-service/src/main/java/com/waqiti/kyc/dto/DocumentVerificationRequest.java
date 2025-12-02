package com.waqiti.kyc.dto;

import com.waqiti.kyc.model.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationRequest {
    
    @NotBlank
    private String userId;
    
    @NotNull
    private DocumentType documentType;
    
    @NotBlank
    private String documentPath; // S3 path or URL
    
    private String documentBackPath; // For documents with two sides
    
    private String documentNumber;
    
    private String issuingCountry;
    
    private LocalDate issueDate;
    
    private LocalDate expiryDate;
    
    // Additional verification parameters
    private boolean extractData; // Extract text/data from document
    
    private boolean validateExpiry;
    
    private boolean checkTampering;
    
    private boolean verifyMRZ; // Machine Readable Zone for passports
    
    private boolean verifyBarcode;
    
    private boolean verifySecurityFeatures;
    
    // Expected data for cross-verification
    private String expectedFirstName;
    
    private String expectedLastName;
    
    private String expectedDateOfBirth;
    
    private String expectedAddress;
}