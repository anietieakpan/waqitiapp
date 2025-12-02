package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Biometric verification result from security service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricVerificationResult {

    private boolean verified;
    private Float confidence;
    private String verificationId;
    private Instant timestamp;
    private String errorMessage;
    private List<String> failureReasons;
    private boolean requiresManualReview;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
}
