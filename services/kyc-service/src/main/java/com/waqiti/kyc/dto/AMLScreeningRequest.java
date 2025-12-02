package com.waqiti.kyc.dto;

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
public class AMLScreeningRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String firstName;
    
    @NotBlank
    private String lastName;
    
    private String middleName;
    
    @NotNull
    private String dateOfBirth;
    
    private String nationality;
    
    @NotBlank
    private String country;
    
    private String address;
    
    private String city;
    
    private String state;
    
    private String postalCode;
    
    // Additional screening parameters
    private boolean includePEP;
    
    private boolean includeSanctions;
    
    private boolean includeAdverseMedia;
    
    private boolean includeFinancialRegulator;
    
    private boolean includeLawEnforcement;
    
    private boolean includeDisqualifiedDirector;
    
    private boolean includeInsolvency;
    
    // Search parameters
    private double fuzziness; // 0.0 to 1.0, where 1.0 is exact match
    
    private String searchType; // "INDIVIDUAL" or "BUSINESS"
    
    // Optional business parameters
    private String businessName;
    
    private String registrationNumber;
    
    private String incorporationCountry;
}