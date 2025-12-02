package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for user details from User Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsDto {
    
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String userTier; // BASIC, SILVER, GOLD, PLATINUM
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private Boolean kycCompleted;
    private String status; // ACTIVE, SUSPENDED, INACTIVE
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private Set<String> roles;
    private UserPreferencesDto preferences;
    
    // Rewards-specific fields
    private Boolean rewardsOptIn;
    private String preferredCurrency;
    private String preferredLanguage;
}