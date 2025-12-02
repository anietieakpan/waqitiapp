package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResponse {
    private String transactionId;
    private String userId;
    private String riskLevel;
    private Double riskScore;
    private Double confidence;
    private Boolean approved;
    private String reason;
    private LocalDateTime checkedAt;
    private List<String> rulesTrigger;
    private List<String> recommendations;
    private Boolean requiresManualReview;
    private Boolean requiresEnhancedMonitoring;
    private Boolean fallbackUsed;
    private String reviewUrl;
    private Integer reviewPriority;
}