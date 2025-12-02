package com.waqiti.payment.dwolla.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Dwolla Customer Request DTO
 * 
 * Request object for creating Dwolla customers.
 */
@Data
@Builder
public class DwollaCustomerRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String type; // "personal" or "business"
    private String ipAddress;
    
    // Address information
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String postalCode;
    
    // Personal information
    private LocalDate dateOfBirth;
    private String ssn; // Last 4 digits for personal customers
    
    // Business information (for business customers)
    private String businessName;
    private String businessType;
    private String businessClassification;
    private String ein; // Employer Identification Number
    private String website;
    private String phone;
    
    // Controller information (for business customers)
    private DwollaController controller;
    
    @Data
    @Builder
    public static class DwollaController {
        private String firstName;
        private String lastName;
        private String title;
        private LocalDate dateOfBirth;
        private String ssn;
        private DwollaAddress address;
        private DwollaPassport passport;
    }
    
    @Data
    @Builder
    public static class DwollaAddress {
        private String address1;
        private String address2;
        private String city;
        private String stateProvinceRegion;
        private String country;
        private String postalCode;
    }
    
    @Data
    @Builder
    public static class DwollaPassport {
        private String number;
        private String country;
    }
}