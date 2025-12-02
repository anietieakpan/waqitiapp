package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressVerificationRequest {
    
    @NotBlank
    private String userId;
    
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
    
    // Optional proof of address document
    private String proofOfAddressPath;
    
    private String documentType; // UTILITY_BILL, BANK_STATEMENT, etc.
    
    // Verification options
    private boolean validatePostalCode;
    
    private boolean standardizeAddress;
    
    private boolean checkDeliverability;
    
    // Additional metadata
    private String addressType; // RESIDENTIAL, BUSINESS, PO_BOX
    
    private String residenceDuration; // How long at this address
}