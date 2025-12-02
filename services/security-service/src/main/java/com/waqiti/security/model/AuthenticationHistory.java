package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication History
 * Summary of recent authentication activity for a user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationHistory {

    private Integer totalAttempts;
    private Integer failedAttempts;
    private Integer successfulAttempts;
    private Integer uniqueLocations;
    private Integer uniqueDevices;
    private Integer blockedAttempts;
    private Double failureRate;
}
