package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationMetrics {
    private String userId;
    private String verificationType;
    private String verificationLevel;
    private LocalDate verificationDate;
    private boolean success;
    private BigDecimal verificationScore;
    private long processingTime;
}
