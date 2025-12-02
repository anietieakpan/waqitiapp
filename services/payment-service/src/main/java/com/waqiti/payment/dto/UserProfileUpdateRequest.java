package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;

import java.time.LocalDate;
import java.util.Map;

/**
 * DTO for user profile update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileUpdateRequest {
    
    private String firstName;
    private String lastName;
    
    @Email
    private String email;
    
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private String occupation;
    
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
    
    // Additional metadata
    private Map<String, Object> metadata;
}