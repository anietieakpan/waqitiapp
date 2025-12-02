package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for check fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckFraudResponse {
    private BigDecimal riskScore;
    private String verificationStatus;
    private List<String> fraudIndicators;
    private String fraudIndicatorsJson;
    private boolean shouldReject;
    private String reason;
    private String recommendedAction;
}