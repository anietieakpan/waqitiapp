package com.waqiti.user.dto.security.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * Result of biometric authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricAuthenticationResult {
    private boolean success;
    private String authToken;
    private String credentialId;
    private double matchScore;
    private String authenticationMethod;
    private String errorCode;
    private String errorMessage;
    private Duration lockoutTimeRemaining;
    private int attemptsRemaining;
}