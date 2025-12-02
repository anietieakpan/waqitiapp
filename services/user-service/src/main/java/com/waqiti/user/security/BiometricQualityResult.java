package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of biometric quality validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricQualityResult {
    private double score;
    private boolean acceptable;
    private List<String> issues;
}