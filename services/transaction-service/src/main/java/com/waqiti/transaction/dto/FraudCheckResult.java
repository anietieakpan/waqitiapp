package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {
    
    private boolean blocked;
    private String reason;
    private Double riskScore;
    private FraudRiskLevel riskLevel;
    private List<String> flaggedRules;
    private List<String> warnings;
    private BigDecimal recommendedLimit;
    private boolean requiresManualReview;
    private String reviewReason;
    private LocalDateTime checkedAt;
    private String checkId;
    
    public enum FraudRiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public static FraudCheckResult allowed() {
        return FraudCheckResult.builder()
            .blocked(false)
            .riskScore(0.0)
            .riskLevel(FraudRiskLevel.LOW)
            .checkedAt(LocalDateTime.now())
            .build();
    }
    
    public static FraudCheckResult blocked(String reason) {
        return FraudCheckResult.builder()
            .blocked(true)
            .reason(reason)
            .riskScore(1.0)
            .riskLevel(FraudRiskLevel.CRITICAL)
            .checkedAt(LocalDateTime.now())
            .build();
    }
}