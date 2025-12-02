package com.waqiti.common.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request for OFAC screening
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OFACScreeningRequest {
    
    private String requestId;
    private String entityName;
    private String entityType; // INDIVIDUAL, ORGANIZATION
    private String firstName;
    private String lastName;
    private String middleName;
    private String dateOfBirth;
    private String nationality;
    private String address;
    private String city;
    private String country;
    private String postalCode;
    private String passportNumber;
    private String nationalId;
    private String phoneNumber;
    private String email;
    private Map<String, String> additionalIdentifiers;
    
    @Builder.Default
    private LocalDateTime requestTime = LocalDateTime.now();
    
    private String requestedBy;
    private String purpose;
    private boolean enhancedScreening;
    private String screeningLevel; // BASIC, STANDARD, ENHANCED, COMPREHENSIVE
    private String jurisdiction; // US, EU, UK, GLOBAL, etc.

    @Builder.Default
    private double matchThreshold = 0.85;
}