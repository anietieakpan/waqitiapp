package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of biometric matching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricMatchResult {
    private double score;
    private String algorithm;
    private long matchingTimeMs;
}