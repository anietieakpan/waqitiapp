package com.waqiti.kyc.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for batch KYC verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchKYCRequest {
    
    @NotBlank(message = "Organization ID is required")
    private String organizationId;
    
    @NotBlank(message = "Requested by is required")
    private String requestedBy;
    
    @NotEmpty(message = "User list cannot be empty")
    @Size(max = 1000, message = "Maximum 1000 users per batch")
    private List<UserKYCRequest> users;
    
    private String provider;
    
    private String verificationLevel = "BASIC";
    
    private boolean notifyOnCompletion = true;
    
    private boolean skipDuplicates = true;
    
    private String callbackUrl;
    
    /**
     * Individual user KYC request within a batch
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserKYCRequest {
        
        @NotBlank(message = "User ID is required")
        private String userId;
        
        @NotBlank(message = "First name is required")
        private String firstName;
        
        @NotBlank(message = "Last name is required")
        private String lastName;
        
        private String email;
        
        private String phoneNumber;
        
        @NotBlank(message = "Date of birth is required")
        private String dateOfBirth;
        
        @NotBlank(message = "Country is required")
        private String country;
        
        private String city;
        
        private String postalCode;
        
        private String streetAddress;
        
        private String state;
        
        // Additional fields
        private String middleName;
        
        private String nationality;
        
        private String occupation;
        
        private String employerName;
    }
}