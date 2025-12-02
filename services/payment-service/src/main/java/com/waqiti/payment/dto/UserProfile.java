package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO representing user profile information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfile {
    
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private String occupation;
    private String status;
    private boolean verified;
    
    // Address information
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    // Preferences
    private String preferredLanguage;
    private String timezone;
    private String currency;
    
    // Metadata
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> metadata;
}