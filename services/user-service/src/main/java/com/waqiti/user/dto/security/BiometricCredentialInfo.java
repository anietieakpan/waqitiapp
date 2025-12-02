package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Biometric credential information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricCredentialInfo {
    private String credentialId;
    private BiometricType biometricType;
    private String deviceFingerprint;
    private Double qualityScore;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private int usageCount;
}