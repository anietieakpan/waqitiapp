package com.waqiti.user.dto.security.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of biometric challenge generation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricChallengeResult {
    private String challenge;
    private long expiresAt;
    private String algorithm;
}