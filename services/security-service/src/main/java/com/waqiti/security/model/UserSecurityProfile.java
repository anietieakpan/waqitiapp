package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * User Security Profile
 * Contains security-related profile information for a user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityProfile {

    private String userId;
    private String riskLevel;
    private Long accountAge;
    private Instant lastLoginTime;
    private LocationData lastLoginLocation;
    private List<Integer> typicalLoginTimes;
    private List<String> typicalDevices;
    private List<String> typicalLocations;
    private Integer totalLogins;
    private Integer failedLoginAttempts;
    private Boolean mfaEnabled;
    private Boolean accountLocked;
    private Instant createdAt;
    private Instant updatedAt;
}
