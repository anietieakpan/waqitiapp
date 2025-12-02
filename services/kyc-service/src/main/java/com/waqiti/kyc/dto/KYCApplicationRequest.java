package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCApplicationRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String userType; // INDIVIDUAL or BUSINESS
    
    @NotBlank
    private String applicationType; // NEW, UPDATE, RESUBMISSION
    
    // Personal Information
    @NotBlank
    private String firstName;
    
    @NotBlank
    private String lastName;
    
    private String middleName;
    
    @NotBlank
    private String dateOfBirth;
    
    @NotBlank
    private String nationality;
    
    @Email
    @NotBlank
    private String email;
    
    @NotBlank
    private String phone;
    
    // Address
    @NotNull
    private AddressDto address;
    
    // Risk Assessment Data
    private BigDecimal expectedTransactionVolume;
    
    private String employmentStatus;
    
    private String sourceOfFunds;
    
    private String purposeOfAccount;
    
    private boolean politicallyExposed;
    
    // Business Information (if applicable)
    private BusinessInfoDto businessInfo;
    
    // Preferences
    private String preferredProvider; // onfido, jumio, complyadvantage
    
    private String preferredLanguage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDto {
        @NotBlank
        private String line1;
        private String line2;
        @NotBlank
        private String city;
        private String state;
        @NotBlank
        private String postalCode;
        @NotBlank
        private String country;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessInfoDto {
        private String businessName;
        private String registrationNumber;
        private String incorporationCountry;
        private String businessType;
        private String industry;
        private Integer numberOfEmployees;
        private BigDecimal annualRevenue;
    }
}