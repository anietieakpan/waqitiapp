package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmploymentVerificationResult {
    private boolean verified;
    private boolean employerExists;
    private String employmentStatus;
    private String provider;
    private BigDecimal verificationScore;
    private boolean requiresManualVerification;
    private Duration estimatedVerificationTime;
}
