package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Biometric capability check request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricCapabilityRequest {
    @NotBlank(message = "Device type is required")
    private String deviceType;
    
    @NotBlank(message = "Platform is required")
    private String platform;
    
    @NotBlank(message = "Browser is required")
    private String browser;
    
    private String browserVersion;
}