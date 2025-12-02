package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Biometric challenge generation request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricChallengeRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Biometric type is required")
    private String biometricType;
}